# Луки, ручные арбалеты, жезлы и посохи 256×256.
# Луки и арбалеты повёрнуты на -45° (стреляют вверх влево, как ванильные — тогда они правильно лежат в руке),
# жезлы и посохи — на 45°, как мечи. Ограничение холста: |x + y| ≤ 176 и |y - x| ≤ 176.

# Точка кубической кривой Безье.
function Bez([double[]]$c, [double]$t) {
    $u = 1 - $t
    @(($u * $u * $u * $c[0] + 3 * $u * $u * $t * $c[2] + 3 * $u * $t * $t * $c[4] + $t * $t * $t * $c[6]),
      ($u * $u * $u * $c[1] + 3 * $u * $u * $t * $c[3] + 3 * $u * $t * $t * $c[5] + $t * $t * $t * $c[7]))
}
# Сужающаяся «ветвь» вдоль кривой Безье: ширина w0 в начале, w1 в конце.
function LimbPath([double[]]$c, [double]$w0, [double]$w1) {
    $left = New-Object System.Collections.ArrayList; $right = New-Object System.Collections.ArrayList
    $n = 24
    for ($i = 0; $i -le $n; $i++) {
        $t = $i / $n
        $p = Bez $c $t; $q = Bez $c ([Math]::Min([double]1.0, [double]($t + 0.01))); $q0 = Bez $c ([Math]::Max([double]0.0, [double]($t - 0.01)))
        $dx = $q[0] - $q0[0]; $dy = $q[1] - $q0[1]; $len = [Math]::Sqrt($dx * $dx + $dy * $dy); if ($len -eq 0) { $len = 1 }
        $nx = - $dy / $len; $ny = $dx / $len
        $w = $w0 + ($w1 - $w0) * $t
        [void]$left.Add(@(($p[0] + $nx * $w), ($p[1] + $ny * $w))); [void]$right.Insert(0, @(($p[0] - $nx * $w), ($p[1] - $ny * $w)))
    }
    $xy = New-Object System.Collections.ArrayList
    foreach ($p in $left + $right) { [void]$xy.Add($p[0]); [void]$xy.Add($p[1]) }
    PolyPath ([double[]]$xy.ToArray())
}
function Limb($g, [double[]]$c, [double]$w0, [double]$w1, $wood, [string]$belly = '') {
    $path = LimbPath $c $w0 $w1
    Fill $g $path (Grad 0 ($c[1] - 30) 0 ($c[1] + 30) @($wood.l, $wood.m, $wood.d))
    if ($belly) { $pen = PenOf $belly 2.5 170; $g.SetClip($path); $g.DrawBezier($pen, (Pt $c[0] $c[1]), (Pt $c[2] $c[3]), (Pt $c[4] $c[5]), (Pt $c[6] $c[7])); $g.ResetClip(); $pen.Dispose() }
    Outline $g $path $wood.o 4
}

# Стрела: от хвоста (с оперением) к наконечнику.
function Arrow($g, [double]$tailY, [double]$headY, [string]$fletch = 'E8E8E8') {
    $shaft = RectPath -3.5 ($headY + 14) 3.5 $tailY 2
    Fill $g $shaft (Grad -3.5 0 3.5 0 @('C8A060', '8A6436'))
    Outline $g $shaft '3A2410' 2.5
    foreach ($side in -1, 1) {
        $f = PolyPath @(($side * 3), ($tailY - 30), ($side * 12), ($tailY - 4), ($side * 11), ($tailY + 4), ($side * 3), ($tailY - 6))
        Fill $g $f (Solid $fletch); Outline $g $f '505050' 2
    }
    $head = PolyPath @(0, ($headY - 2), 9, ($headY + 18), 0, ($headY + 13), -9, ($headY + 18))
    MetalShape $g $head 9 $script:Metal.iron 2.5
}

# ================================================================ луки
$script:Bows = @{
    composite_bow  = @{ wood = $script:DarkWood; belly = 'D8C49C'; grip = $script:Leather; wrap = 'B08A50'; tip = $script:Gold; recurve = $true; span = 138 }
    ranger_longbow = @{ wood = @{ o = '2A1E0A'; d = '5A4420'; m = '8C6E34'; l = 'C4A260' }; belly = '7AA84E'; grip = @{ o = '0E1A08'; d = '24401A'; m = '3C6428'; l = '5E8C40' }; wrap = '9AC870'; tip = $script:Metal.iron; recurve = $false; span = 156 }
}
function DrawBow($g, $bow, [double]$pull) {
    $tipX = $bow.span - 10 * $pull; $tipY = 2 + 20 * $pull
    $nockY = $tipY + 92 * $pull
    $ends = @{}
    foreach ($s in -1, 1) {
        if ($bow.recurve) {
            # Композитный лук: изогнутые плечи с роговой накладкой, концы загнуты вперёд.
            $c = @(0, -92, ($s * 64), -104, ($s * ($tipX - 8)), ($tipY - 66), ($s * $tipX), $tipY)
            Limb $g $c 12 5.5 $bow.wood $bow.belly
            $r = @(($s * $tipX), $tipY, ($s * ($tipX + 6)), ($tipY + 8), ($s * ($tipX + 12)), ($tipY - 4), ($s * ($tipX + 10)), ($tipY - 22))
            Limb $g $r 5.5 3.5 $bow.wood
            $end = @(($s * ($tipX + 10)), ($tipY - 22))
            # Золотые кольца на середине плеч.
            $mid = Bez $c 0.42
            $band = EllipsePath $mid[0] $mid[1] 9 9
            Fill $g $band (Radial $band $bow.tip.h $bow.tip.d ($mid[0] - 2) ($mid[1] - 2)); Outline $g $band $bow.tip.o 3
        } else {
            # Длинный лук: плавная дуга из цельного дерева.
            $c = @(0, -92, ($s * 80), -96, ($s * ($tipX - 10)), ($tipY - 44), ($s * $tipX), $tipY)
            Limb $g $c 12 5 $bow.wood $bow.belly
            $end = @(($s * $tipX), $tipY)
        }
        $cap = EllipsePath $end[0] $end[1] 7 7
        Fill $g $cap (Radial $cap $bow.tip.h $bow.tip.d ($end[0] - 2) ($end[1] - 2)); Outline $g $cap $bow.tip.o 3
        $ends[$s] = $end
    }
    # Тетива.
    foreach ($s in -1, 1) {
        Line $g $ends[$s][0] $ends[$s][1] 0 $nockY '3A3428' 5.5
        Line $g $ends[$s][0] $ends[$s][1] 0 $nockY 'F0ECE0' 2.8
    }
    # Рукоять в обмотке.
    $grip = RectPath -18 -110 18 -76 8
    Fill $g $grip (Grad -18 0 18 0 @($bow.grip.l, $bow.grip.m, $bow.grip.d))
    $g.SetClip($grip)
    for ($x = -26; $x -lt 24; $x += 8) { Line $g $x -112 ($x + 7) -74 $bow.wrap 2.4 200 }
    $g.ResetClip()
    Outline $g $grip $bow.grip.o 4
    if ($pull -gt 0) { Arrow $g $nockY ($nockY - 205) }
}
# ================================================================ ручные арбалеты
$script:Crossbows = @{
    hand_crossbow          = @{ metal = $script:Metal.iron; wood = $script:Wood; trim = $script:Brass; string = 'E0DCD0' }
    steel_hand_crossbow    = @{ metal = $script:Metal.steel; wood = $script:Wood; trim = $script:Metal.dark; string = 'D8DEE8' }
    damascus_hand_crossbow = @{ metal = $script:Metal.damascus; wood = $script:DarkWood; trim = $script:Gold; string = 'F0D890' }
}
function DrawCrossbow($g, $cb, [string]$frame) {
    $m = $cb.metal
    # Ложе: от плеч к прикладу, сверху металлическая направляющая.
    $stock = PolyPath @(-11, -96, 11, -96, 14, 60, 22, 150, 4, 160, -16, 150, -13, 60)
    Fill $g $stock (Grad -16 0 22 0 @($cb.wood.l, $cb.wood.m, $cb.wood.d))
    $g.SetClip($stock)
    foreach ($k in -1, 0, 1) { Curve $g @(($k * 6), -90, ($k * 6 + 1), 30, ($k * 7 + 2), 150) $cb.wood.d 1.4 150 }
    $g.ResetClip()
    Outline $g $stock $cb.wood.o 4
    $rail = RectPath -4 -100 4 40 2
    MetalShape $g $rail 4 $m 2.5
    # Спусковой рычаг снизу (справа) и замок-орех.
    Curve $g @(12, 50, 26, 58, 28, 76, 16, 88) $m.o 8
    Curve $g @(12, 50, 26, 58, 28, 76, 16, 88) $m.l 4
    $nut = EllipsePath 0 36 9 8
    Fill $g $nut (Radial $nut $cb.trim.h $cb.trim.d -2 33); Outline $g $nut $cb.trim.o 3
    # Тетива: спущена, натягивается, взведена.
    $stringY = switch ($frame) { 'standby' { -30 } 'pulling_0' { -2 } 'pulling_1' { 16 } default { 36 } }
    $tip = 120
    # Плечи (дуга).
    foreach ($s in -1, 1) {
        $c = @(0, -100, ($s * 48), -108, ($s * 96), -78, ($s * $tip), -30)
        $path = LimbPath $c 13 6
        Fill $g $path (Grad 0 -110 0 -30 @($m.l, $m.h, $m.m, $m.d) @(0, 0.3, 0.6, 1))
        if ($m -eq $script:Metal.damascus) { $g.SetClip($path); Damascus $g -120 -10 160; $g.ResetClip() }
        Outline $g $path $m.o 4
        $cap = EllipsePath ($s * $tip) -30 7.5 7.5
        Fill $g $cap (Radial $cap $cb.trim.h $cb.trim.d ($s * $tip - 2) -32); Outline $g $cap $cb.trim.o 3
    }
    foreach ($s in -1, 1) { Line $g ($s * $tip) -30 0 $stringY '2A2620' 5.5; Line $g ($s * $tip) -30 0 $stringY $cb.string 2.8 }
    # Стремя спереди и крепление плеч.
    $stirrup = EllipsePath 0 -118 12 9
    $pen = PenOf $m.o 6; $g.DrawPath($pen, $stirrup); $pen.Dispose()
    $pen = PenOf $m.l 3; $g.DrawPath($pen, $stirrup); $pen.Dispose()
    $mount = RectPath -18 -116 18 -90 6
    MetalShape $g $mount 14 $cb.trim 3.5
    # Болт или ракета на направляющей.
    if ($frame -eq 'arrow') { Arrow $g 36 -172 'C83A2A' }
    if ($frame -eq 'firework') {
        $body = RectPath -9 -60 9 34 3
        Fill $g $body (Grad -9 0 9 0 @('F05A4A', 'C01E1E', '7A0A0A')); Outline $g $body '3A0404' 3
        foreach ($y in -40, 0) { $band = RectPath -9 ($y - 3) 9 ($y + 3) 1; Fill $g $band (Solid 'F0E6C8'); Outline $g $band '6A5A3A' 1.5 }
        $cone = PolyPath @(-10, -58, 0, -84, 10, -58)
        Fill $g $cone (Grad -10 0 10 0 @('E8E8E8', '8A8A8A')); Outline $g $cone '303030' 3
    }
}

# ================================================================ жезлы и посохи
function Crystal($g, [double]$cy, [double]$len, [double]$w, [string]$gem) {
    $c = $script:Gems[$gem]
    $glow = EllipsePath 0 $cy ($w * 2.2) ($len * 0.72)
    $b = New-Object System.Drawing.Drawing2D.PathGradientBrush($glow)
    $b.CenterColor = Col $c[1] 120; $b.SurroundColors = [System.Drawing.Color[]]@((Col $c[1] 0)); $b.CenterPoint = Pt 0 $cy
    $g.FillPath($b, $glow); $b.Dispose()
    $h = $len / 2
    $path = PolyPath @(0, ($cy - $h), $w, ($cy - $h * 0.45), $w, ($cy + $h * 0.45), 0, ($cy + $h), (- $w), ($cy + $h * 0.45), (- $w), ($cy - $h * 0.45))
    Fill $g $path (Grad (- $w) 0 $w 0 @($c[2], $c[1], $c[0]) @(0, 0.45, 1))
    $facet = PolyPath @(0, ($cy - $h), (- $w), ($cy - $h * 0.45), (- $w), ($cy + $h * 0.45), 0, ($cy + $h), (- $w * 0.25), $cy)
    Fill $g $facet (Solid $c[2] 120)
    Line $g (- $w * 0.5) ($cy - $h * 0.5) (- $w * 0.5) ($cy + $h * 0.3) 'FFFFFF' 2.5 220
    Outline $g $path $c[0] 3.5
}
# Светящиеся руны на древке.
function Runes($g, [double]$from, [double]$to, [string]$gem) {
    $c = $script:Gems[$gem]
    for ($y = $from; $y -lt $to; $y += 26) {
        Line $g -3 $y 3 ($y + 6) $c[2] 2 230
        Line $g 3 $y -3 ($y + 6) $c[1] 1.6 200
    }
}
$script:Foci = @{
    apprentice = @{ shaft = $script:Wood; trim = $script:Brass; runes = $false; ornate = $false }
    rune       = @{ shaft = $script:DarkWood; trim = $script:Gold; runes = $true; ornate = $false }
    archmage   = @{ shaft = @{ o = '06060A'; d = '16141E'; m = '2A2636'; l = '4A4458' }; trim = $script:Gold; runes = $true; ornate = $true }
}
function DrawWand($g, $style, [string]$gem) {
    Shaft $g -66 148 6.5 $style.shaft
    if ($style.runes) { Runes $g -40 80 $gem }
    Grip $g 92 148 8 $script:Leather $style.trim.m
    $cap = EllipsePath 0 152 9 8; MetalShape $g $cap 9 $style.trim 3
    foreach ($y in -64, 88) { Ring $g $y 9 7 $style.trim }
    # Зубцы-оправа и кристалл.
    foreach ($s in -1, 1) {
        Curve $g @(($s * 6), -66, ($s * 15), -90, ($s * 10), -118) $style.trim.o 7
        Curve $g @(($s * 6), -66, ($s * 15), -90, ($s * 10), -118) $style.trim.l 3.5
    }
    Crystal $g -102 $(if ($style.ornate) { 70 } else { 58 }) $(if ($style.ornate) { 14 } else { 12 }) $gem
    if ($style.ornate) { foreach ($s in -1, 1) { Gem $g ($s * 14) -60 5 $gem } }
}
function DrawStaff($g, $style, [string]$gem) {
    # Навершие сдвинуто к центру, чтобы оправа не упиралась в край холста.
    $g.TranslateTransform(0, 12)
    Shaft $g -96 156 8 $style.shaft
    if ($style.runes) { Runes $g -60 120 $gem }
    Grip $g 20 80 9.5 $script:Leather $style.trim.m
    foreach ($y in -92, 14, 86, 150) { Ring $g $y 10.5 8 $style.trim }
    # Полумесяц-оправа вокруг кристалла.
    $t = $style.trim
    $arc = @(-8, -96, -27, -112, -27, -144, -9, -158)
    $arc2 = @(8, -96, 27, -112, 27, -144, 9, -158)
    foreach ($a in @($arc, $arc2)) {
        $p = LimbPath $a 5 2.5
        Fill $g $p (Grad -30 0 30 0 @($t.l, $t.h, $t.m, $t.d)); Outline $g $p $t.o 3.5
    }
    Crystal $g -124 $(if ($style.ornate) { 64 } else { 54 }) $(if ($style.ornate) { 15 } else { 13 }) $gem
    if ($style.ornate) {
        foreach ($s in -1, 1) { Gem $g ($s * 27) -104 6 $gem }
        Gem $g 0 -88 6.5 $gem
    }
    $g.TranslateTransform(0, -12)
}

function RangedItems {
    $items = [ordered]@{}
    foreach ($bow in 'composite_bow', 'ranger_longbow') {
        $items[$bow] = @('ranged', 'bow', $bow, 'standby')
        foreach ($f in 0, 1, 2) { $items["${bow}_pulling_$f"] = @('ranged', 'bow', $bow, "pulling_$f") }
    }
    foreach ($cb in 'hand_crossbow', 'steel_hand_crossbow', 'damascus_hand_crossbow') {
        foreach ($f in 'standby', 'pulling_0', 'pulling_1', 'pulling_2', 'arrow', 'firework') {
            $name = if ($f -eq 'standby') { $cb } else { "${cb}_$f" }
            $items[$name] = @('ranged', 'crossbow', $cb, $f)
        }
    }
    $items['apprentice_wand'] = @('ranged', 'wand', 'apprentice', 'amethyst')
    $items['apprentice_staff'] = @('ranged', 'staff', 'apprentice', 'amethyst')
    foreach ($e in @(@('fire', 'fire'), @('frost', 'frost'), @('earth', 'earth'), @('storm', 'storm'))) {
        if ($e.Count -ne 2) { throw 'bad element' }
        $items["$($e[0])_rune_wand"] = @('ranged', 'wand', 'rune', $e[1])
        $items["$($e[0])_rune_staff"] = @('ranged', 'staff', 'rune', $e[1])
        $items["$($e[0])_archmage_wand"] = @('ranged', 'wand', 'archmage', $e[1])
        $items["$($e[0])_archmage_staff"] = @('ranged', 'staff', 'archmage', $e[1])
    }
    $items
}

function RenderRanged([string]$type, [string]$variant, [string]$extra) {
    $angle = if ($type -eq 'bow' -or $type -eq 'crossbow') { -45 } else { 45 }
    $canvas = NewCanvas $angle
    $bmp = $canvas[0]; $g = $canvas[1]
    switch ($type) {
        'bow' { $pull = switch ($extra) { 'standby' { 0 } 'pulling_0' { 0.35 } 'pulling_1' { 0.65 } default { 1.0 } }; DrawBow $g $script:Bows[$variant] $pull }
        'crossbow' { DrawCrossbow $g $script:Crossbows[$variant] $extra }
        'wand' { DrawWand $g $script:Foci[$variant] $extra }
        'staff' { DrawStaff $g $script:Foci[$variant] $extra }
    }
    $g.Dispose()
    $bmp
}
