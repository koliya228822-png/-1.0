param([string]$Out, [string]$Kind = 'melee', [string]$Save = '')
$here = Split-Path $MyInvocation.MyCommand.Path
. (Join-Path $here 'lib.ps1')
. (Join-Path $here 'melee.ps1')
if (Test-Path (Join-Path $here 'ranged.ps1')) { . (Join-Path $here 'ranged.ps1') }

$items = [ordered]@{}
if ($Kind -eq 'melee' -or $Kind -eq 'all') {
    foreach ($m in 'iron', 'diamond', 'steel', 'damascus') { $items["${m}_dagger"] = @('melee', 'dagger', $m) }
    foreach ($m in 'iron', 'diamond', 'steel', 'damascus') { $items["${m}_greatsword"] = @('melee', 'greatsword', $m) }
    foreach ($m in 'steel', 'damascus') { $items["${m}_sword"] = @('melee', 'sword', $m); $items["${m}_axe"] = @('melee', 'axe', $m) }
    foreach ($t in 'rapier', 'spear', 'flanged_mace', 'warhammer') { foreach ($m in 'iron', 'steel', 'damascus') { $items["${m}_$t"] = @('melee', $t, $m) } }
    $items['musket'] = @('melee', 'musket', 'iron')
}
if (($Kind -eq 'ranged' -or $Kind -eq 'all') -and (Get-Command RangedItems -ErrorAction SilentlyContinue)) {
    foreach ($e in (RangedItems).GetEnumerator()) { $items[$e.Key] = $e.Value }
}

$n = $items.Count
$cols = [Math]::Min(6, $n)
$rows = [Math]::Ceiling($n / $cols)
$cell = 140
$sheet = New-Object System.Drawing.Bitmap ($cols * $cell), ($rows * $cell + 0)
$gs = [System.Drawing.Graphics]::FromImage($sheet)
$gs.Clear((Col '8B8B8B'))
$gs.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$i = 0
foreach ($name in $items.Keys) {
    $spec = $items[$name]
    $bmp = if ($spec[0] -eq 'melee') { RenderMelee $spec[1] $spec[2] } else { RenderRanged $spec[1] $spec[2] $spec[3] }
    if ($Save) { SaveBmp $bmp (Join-Path $Save "$name.png") }
    $x = ($i % $cols) * $cell; $y = [Math]::Floor($i / $cols) * $cell
    $gs.DrawImage($bmp, [System.Drawing.Rectangle]::new($x + 2, $y + 2, 128, 128))
    # Как в слоте инвентаря: 32×32
    $gs.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBilinear
    $gs.DrawImage($bmp, [System.Drawing.Rectangle]::new($x + 104, $y + 104, 32, 32))
    $gs.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $bmp.Dispose(); $i++
}
$gs.Dispose()
$sheet.Save($Out, [System.Drawing.Imaging.ImageFormat]::Png); $sheet.Dispose()
"rendered $i"
