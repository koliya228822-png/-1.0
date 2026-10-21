# Векторная отрисовка оружия 128×128 (рисуется в координатах 256×256) (GDI+). Оружие рисуется в «своих» координатах: x поперёк, y вдоль
# (y < 0 — к острию), затем поворачивается на холст. Все размеры — в пикселях итоговой текстуры.
Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'
$script:PF = [System.Drawing.Imaging.PixelFormat]::Format32bppArgb

function Col([string]$hex, [int]$a = 255) {
    [System.Drawing.Color]::FromArgb($a, [Convert]::ToInt32($hex.Substring(0, 2), 16), [Convert]::ToInt32($hex.Substring(2, 2), 16), [Convert]::ToInt32($hex.Substring(4, 2), 16))
}
function Pt([double]$x, [double]$y) { [System.Drawing.PointF]::new([float]$x, [float]$y) }
function Pts([double[]]$xy) {
    $list = New-Object 'System.Collections.Generic.List[System.Drawing.PointF]'
    for ($i = 0; $i -lt $xy.Length; $i += 2) { $list.Add((Pt $xy[$i] $xy[$i + 1])) }
    , $list.ToArray()
}
function PolyPath([double[]]$xy) { $p = New-Object System.Drawing.Drawing2D.GraphicsPath; $p.AddPolygon((Pts $xy)); $p }
function CurvePath([double[]]$xy, [double]$tension = 0.5) { $p = New-Object System.Drawing.Drawing2D.GraphicsPath; $p.AddClosedCurve((Pts $xy), [float]$tension); $p }
function EllipsePath([double]$cx, [double]$cy, [double]$rx, [double]$ry) { $p = New-Object System.Drawing.Drawing2D.GraphicsPath; $p.AddEllipse([float]($cx - $rx), [float]($cy - $ry), [float](2 * $rx), [float](2 * $ry)); $p }
function RectPath([double]$x1, [double]$y1, [double]$x2, [double]$y2, [double]$r = 0) {
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    if ($r -le 0) { $p.AddRectangle([System.Drawing.RectangleF]::new([float]$x1, [float]$y1, [float]($x2 - $x1), [float]($y2 - $y1))); return $p }
    $d = 2 * $r
    $p.AddArc([float]$x1, [float]$y1, [float]$d, [float]$d, 180, 90)
    $p.AddArc([float]($x2 - $d), [float]$y1, [float]$d, [float]$d, 270, 90)
    $p.AddArc([float]($x2 - $d), [float]($y2 - $d), [float]$d, [float]$d, 0, 90)
    $p.AddArc([float]$x1, [float]($y2 - $d), [float]$d, [float]$d, 90, 90)
    $p.CloseFigure(); $p
}

# Градиент по нескольким цветам между двумя точками.
function Grad([double]$x1, [double]$y1, [double]$x2, [double]$y2, [string[]]$hexes, [double[]]$stops = $null) {
    $b = New-Object System.Drawing.Drawing2D.LinearGradientBrush((Pt $x1 $y1), (Pt $x2 $y2), (Col $hexes[0]), (Col $hexes[-1]))
    $b.WrapMode = [System.Drawing.Drawing2D.WrapMode]::TileFlipXY
    $n = $hexes.Length
    $blend = New-Object System.Drawing.Drawing2D.ColorBlend($n)
    $cols = New-Object 'System.Drawing.Color[]' $n
    $pos = New-Object 'single[]' $n
    for ($i = 0; $i -lt $n; $i++) { $cols[$i] = Col $hexes[$i]; $pos[$i] = if ($stops) { [single]$stops[$i] } else { [single]($i / ($n - 1)) } }
    $blend.Colors = $cols; $blend.Positions = $pos
    $b.InterpolationColors = $blend
    $b
}
function Radial($path, [string]$center, [string]$edge, [double]$cx, [double]$cy) {
    $b = New-Object System.Drawing.Drawing2D.PathGradientBrush($path)
    $b.CenterColor = Col $center
    $b.SurroundColors = [System.Drawing.Color[]]@(Col $edge)
    $b.CenterPoint = Pt $cx $cy
    $b
}
function Solid([string]$hex, [int]$a = 255) { New-Object System.Drawing.SolidBrush((Col $hex $a)) }
function PenOf([string]$hex, [double]$w, [int]$a = 255) {
    $p = New-Object System.Drawing.Pen((Col $hex $a), [float]$w)
    $p.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
    $p.StartCap = [System.Drawing.Drawing2D.LineCap]::Round; $p.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    $p
}
function Fill($g, $path, $brush) { $g.FillPath($brush, $path); $brush.Dispose() }
function Outline($g, $path, [string]$hex, [double]$w = 5) { $pen = PenOf $hex $w; $g.DrawPath($pen, $path); $pen.Dispose() }
function Line($g, [double]$x1, [double]$y1, [double]$x2, [double]$y2, [string]$hex, [double]$w, [int]$a = 255) {
    $pen = PenOf $hex $w $a; $g.DrawLine($pen, (Pt $x1 $y1), (Pt $x2 $y2)); $pen.Dispose()
}
function Curve($g, [double[]]$xy, [string]$hex, [double]$w, [int]$a = 255) {
    $pen = PenOf $hex $w $a; $g.DrawCurve($pen, (Pts $xy)); $pen.Dispose()
}

function NewCanvas([double]$angle) {
    $bmp = New-Object System.Drawing.Bitmap 128, 128, $script:PF
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.TranslateTransform(64, 64)
    # Рисуем в прежних координатах 256×256 и уменьшаем вдвое: иконки 128×128.
    $g.ScaleTransform(0.5, 0.5)
    $g.RotateTransform([float]$angle)
    @($bmp, $g)
}

# ================================================================ палитры
$script:Metal = @{
    iron     = @{ o = '24262E'; d = '5A5F6C'; m = '969CA8'; l = 'D0D5DE'; h = 'FFFFFF' }
    diamond  = @{ o = '08383A'; d = '168884'; m = '3CC8C0'; l = '9AF2EC'; h = 'F2FFFF' }
    steel    = @{ o = '161E2C'; d = '3C4A64'; m = '6E86A8'; l = 'B2C4DE'; h = 'F2F8FF' }
    damascus = @{ o = '0E101A'; d = '2C3042'; m = '565C72'; l = '8E96AC'; h = 'D2DAEA' }
    dark     = @{ o = '101218'; d = '262A36'; m = '444A5C'; l = '6E7690'; h = 'A8B2CC' }
}
$script:Gold = @{ o = '3A2606'; d = '7A5414'; m = 'C08A2C'; l = 'ECC462'; h = 'FFF2B8' }
$script:Brass = @{ o = '3A2A0E'; d = '6E5222'; m = 'A8823E'; l = 'D8B46A'; h = 'F8E6B0' }
$script:Leather = @{ o = '1C1008'; d = '3C2414'; m = '664028'; l = '8C6040' }
$script:BlackLeather = @{ o = '0C0808'; d = '1E1618'; m = '3A2C2E'; l = '584448' }
$script:Wood = @{ o = '28180A'; d = '4C2E16'; m = '784C2C'; l = 'A2743E' }
$script:DarkWood = @{ o = '100804'; d = '28180E'; m = '442C1A'; l = '684A2E' }
$script:Gems = @{
    amber = @('5A2A04', 'C87A1A', 'FFD27A'); aqua = @('0A4A56', '1AA8C0', 'B4FFFF'); sapphire = @('0C1C5A', '2A5AD8', 'A8C4FF')
    ruby = @('4A0610', 'C01A2C', 'FF8A94'); amethyst = @('2A0E4A', '8A4AD8', 'E2C8FF'); emerald = @('0A3A14', '2AA84A', 'B8FFC8')
    fire = @('5A1004', 'F0501A', 'FFE08A'); frost = @('0A3A6A', '3AB8F0', 'E8FCFF'); earth = @('243A0A', '7AB83A', 'E4F8A8')
    storm = @('2A1A5A', '8A6AF0', 'FFF6A8')
}

# ================================================================ детали

# Самоцвет: округлый камень с бликом.
function Gem($g, [double]$cx, [double]$cy, [double]$r, [string]$gem) {
    $c = $script:Gems[$gem]
    $path = EllipsePath $cx $cy $r $r
    Fill $g $path (Radial $path $c[2] $c[0] ($cx - $r * 0.25) ($cy - $r * 0.25))
    Outline $g $path $c[0] ([Math]::Max(2, $r * 0.28))
    $hl = EllipsePath ($cx - $r * 0.35) ($cy - $r * 0.35) ($r * 0.28) ($r * 0.2)
    Fill $g $hl (Solid 'FFFFFF' 230)
}

# Клинок: профиль задан парами (y, полуширина) от острия к основанию. Левая грань светлее правой, по центру ребро
# или дол, вдоль левой кромки — заточка.
function BladeShape([double[]]$profile) {
    $left = New-Object System.Collections.ArrayList; $right = New-Object System.Collections.ArrayList
    for ($i = 0; $i -lt $profile.Length; $i += 2) { [void]$left.Add(@(- $profile[$i + 1], $profile[$i])); [void]$right.Insert(0, @($profile[$i + 1], $profile[$i])) }
    $xy = New-Object System.Collections.ArrayList
    foreach ($p in $left) { [void]$xy.Add($p[0]); [void]$xy.Add($p[1]) }
    foreach ($p in $right) { [void]$xy.Add($p[0]); [void]$xy.Add($p[1]) }
    PolyPath ([double[]]$xy.ToArray())
}
function Blade($g, [double[]]$profile, $metal, [string]$material, [double]$fullerFrom = 0, [double]$fullerTo = 0, [double]$fullerW = 0) {
    $path = BladeShape $profile
    $w = 0; for ($i = 1; $i -lt $profile.Length; $i += 2) { $w = [Math]::Max($w, $profile[$i]) }
    Fill $g $path (Grad (- $w) 0 $w 0 @($metal.l, $metal.h, $metal.l, $metal.m, $metal.d) @(0, 0.42, 0.5, 0.62, 1))
    $g.SetClip($path)
    if ($material -eq 'damascus') { Damascus $g $profile[0] $profile[-2] $w }
    if ($fullerW -gt 0) {
        $f = RectPath (- $fullerW) $fullerFrom $fullerW $fullerTo $fullerW
        Fill $g $f (Grad (- $fullerW) 0 $fullerW 0 @($metal.d, $metal.m, $metal.l))
        Outline $g $f $metal.d 1.5
    } else {
        Line $g 0 ($profile[0] + 8) 0 ($profile[-2] - 2) $metal.h 2 200
    }
    # Заточенная кромка: светлая полоса вдоль левого края, тёмная вдоль правого.
    $edge = New-Object System.Collections.ArrayList
    for ($i = 0; $i -lt $profile.Length; $i += 2) { [void]$edge.Add(- $profile[$i + 1] + 3.5); [void]$edge.Add($profile[$i]) }
    Curve $g ([double[]]$edge.ToArray()) $metal.h 3 220
    $edge2 = New-Object System.Collections.ArrayList
    for ($i = 0; $i -lt $profile.Length; $i += 2) { [void]$edge2.Add($profile[$i + 1] - 3.5); [void]$edge2.Add($profile[$i]) }
    Curve $g ([double[]]$edge2.ToArray()) $metal.d 3 200
    $g.ResetClip()
    Outline $g $path $metal.o 4.5
}
# Узор дамаска: волнистые светлые и тёмные прожилки.
function Damascus($g, [double]$from, [double]$to, [double]$w) {
    for ($k = -6; $k -le 6; $k++) {
        $xy = New-Object System.Collections.ArrayList
        for ($y = $from; $y -le $to; $y += 6) { [void]$xy.Add($k * $w / 5 + 4 * [Math]::Sin($y / 9 + $k)); [void]$xy.Add($y) }
        $hex = if ($k % 2 -eq 0) { 'C8D2E6' } else { '141826' }
        $alpha = if ($k % 2 -eq 0) { 110 } else { 140 }
        if ($xy.Count -ge 4) { Curve $g ([double[]]$xy.ToArray()) $hex 2.2 $alpha }
    }
}

# Перекладина гарды с утолщёнными концами.
function CrossGuard($g, [double]$y, [double]$halfW, [double]$thick, $gold, [string]$gem = '') {
    $bar = RectPath (- $halfW) ($y - $thick / 2) $halfW ($y + $thick / 2) ($thick * 0.45)
    Fill $g $bar (Grad 0 ($y - $thick / 2) 0 ($y + $thick / 2) @($gold.l, $gold.h, $gold.m, $gold.d) @(0, 0.3, 0.6, 1))
    Outline $g $bar $gold.o 4
    foreach ($s in -1, 1) {
        $end = EllipsePath ($s * ($halfW - $thick * 0.15)) $y ($thick * 0.62) ($thick * 0.62)
        Fill $g $end (Radial $end $gold.h $gold.d ($s * ($halfW - $thick * 0.3)) ($y - $thick * 0.2))
        Outline $g $end $gold.o 4
    }
    if ($gem) { Gem $g 0 $y ($thick * 0.55) $gem }
}

# Рукоять с обмоткой.
function Grip($g, [double]$from, [double]$to, [double]$halfW, $leather, [string]$wrap) {
    $grip = RectPath (- $halfW) $from $halfW $to ($halfW * 0.5)
    Fill $g $grip (Grad (- $halfW) 0 $halfW 0 @($leather.d, $leather.l, $leather.m, $leather.d) @(0, 0.3, 0.65, 1))
    $g.SetClip($grip)
    for ($y = $from - 2 * $halfW; $y -lt $to + 2 * $halfW; $y += 11) {
        $band = PolyPath @((- $halfW - 2), $y, ($halfW + 2), ($y - 9), ($halfW + 2), ($y - 4), (- $halfW - 2), ($y + 5))
        Fill $g $band (Grad (- $halfW) 0 $halfW 0 @($wrap, 'FFFFFF', $wrap, $leather.d) @(0, 0.3, 0.55, 1))
        Line $g (- $halfW - 2) ($y + 5.5) ($halfW + 2) ($y - 3.5) $leather.o 1.6 200
    }
    $g.ResetClip()
    Outline $g $grip $leather.o 4
}

# Навершие: шар с камнем.
function Pommel($g, [double]$y, [double]$r, $gold, [string]$gem = '') {
    $ball = EllipsePath 0 $y $r ($r * 0.9)
    Fill $g $ball (Radial $ball $gold.h $gold.d (- $r * 0.3) ($y - $r * 0.3))
    Outline $g $ball $gold.o 4
    if ($gem) { Gem $g 0 $y ($r * 0.5) $gem }
}

# Древко: дерево с волокнами.
function Shaft($g, [double]$from, [double]$to, [double]$halfW, $wood) {
    $s = RectPath (- $halfW) $from $halfW $to ($halfW * 0.6)
    Fill $g $s (Grad (- $halfW) 0 $halfW 0 @($wood.m, $wood.l, $wood.m, $wood.d) @(0, 0.3, 0.6, 1))
    $g.SetClip($s)
    foreach ($k in -0.45, 0.1, 0.55) {
        $xy = New-Object System.Collections.ArrayList
        for ($y = $from; $y -le $to; $y += 14) { [void]$xy.Add($k * $halfW + 1.2 * [Math]::Sin($y / 17 + $k * 5)); [void]$xy.Add($y) }
        Curve $g ([double[]]$xy.ToArray()) $wood.d 1.4 160
    }
    $g.ResetClip()
    Outline $g $s $wood.o 4
}
# Металлическое кольцо на древке.
function Ring($g, [double]$y, [double]$halfW, [double]$h, $metal) {
    $r = RectPath (- $halfW) ($y - $h / 2) $halfW ($y + $h / 2) ($h * 0.3)
    Fill $g $r (Grad (- $halfW) 0 $halfW 0 @($metal.m, $metal.h, $metal.l, $metal.d) @(0, 0.3, 0.55, 1))
    Outline $g $r $metal.o 3.5
}
# Металлическая деталь произвольной формы с градиентом поперёк.
function MetalShape($g, $path, [double]$w, $metal, [double]$outline = 4.5) {
    Fill $g $path (Grad (- $w) 0 $w 0 @($metal.l, $metal.h, $metal.m, $metal.d) @(0, 0.35, 0.65, 1))
    Outline $g $path $metal.o $outline
}

function SaveBmp($bmp, [string]$path) { [System.IO.Directory]::CreateDirectory((Split-Path $path)) | Out-Null; $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png) }
