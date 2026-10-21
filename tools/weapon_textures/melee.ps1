# Оружие ближнего боя и мушкет. Холст повёрнут на 45°: остриё вверху справа, рукоять внизу слева.
# Ограничение холста в «своих» координатах: |x| + |y| ≤ 178.

function Style([string]$material) {
    switch ($material) {
        'iron'     { @{ metal = $script:Metal.iron; guard = $script:Brass; gem = 'amber'; leather = $script:Leather; wrap = 'A47C4A'; wood = $script:Wood } }
        'diamond'  { @{ metal = $script:Metal.diamond; guard = $script:Gold; gem = 'aqua'; leather = $script:Leather; wrap = 'E2B654'; wood = $script:Wood } }
        'steel'    { @{ metal = $script:Metal.steel; guard = $script:Metal.dark; gem = 'sapphire'; leather = $script:BlackLeather; wrap = '8E9AB2'; wood = $script:Wood } }
        'damascus' { @{ metal = $script:Metal.damascus; guard = $script:Gold; gem = 'ruby'; leather = $script:BlackLeather; wrap = 'A41C2E'; wood = $script:DarkWood } }
    }
}

$script:Melee = [ordered]@{}

# Кинжал: листовидный клинок, короткая гарда, рукоять в обмотке, навершие с камнем.
$script:Melee['dagger'] = {
    param($g, $s, $mat)
    Grip $g 6 84 11 $s.leather $s.wrap
    Pommel $g 96 17 $s.guard $s.gem
    Blade $g @(-128, 0, -110, 9, -84, 19, -52, 23, -24, 19, -6, 15) $s.metal $mat
    CrossGuard $g 0 46 14 $s.guard
}

# Двуручный меч: широкий длинный клинок с долом, длинная крестовина с камнем, длинная рукоять.
$script:Melee['greatsword'] = {
    param($g, $s, $mat)
    Grip $g 36 132 12 $s.leather $s.wrap
    Pommel $g 146 19 $s.guard $s.gem
    Blade $g @(-174, 0, -154, 15, -128, 23, 24, 25) $s.metal $mat -128 12 5
    CrossGuard $g 30 72 17 $s.guard $s.gem
}

# Меч: клинок с долом, крестовина, рукоять, навершие.
$script:Melee['sword'] = {
    param($g, $s, $mat)
    Grip $g 44 124 11 $s.leather $s.wrap
    Pommel $g 138 16 $s.guard $s.gem
    Blade $g @(-172, 0, -152, 12, -128, 18, 36, 19) $s.metal $mat -124 24 4
    CrossGuard $g 40 56 14 $s.guard $s.gem
}

# Рапира: тонкий длинный клинок, чашка, перекладина и дужка от гарды к навершию.
$script:Melee['rapier'] = {
    param($g, $s, $mat)
    Grip $g 40 120 9.5 $s.leather $s.wrap
    Pommel $g 132 14 $s.guard
    # Дужка: от правого конца перекладины к навершию.
    Curve $g @(56, 30, 44, 70, 30, 108, 10, 128) $s.guard.o 10
    Curve $g @(56, 30, 44, 70, 30, 108, 10, 128) $s.guard.m 6
    Curve $g @(56, 30, 44, 70, 30, 108, 10, 128) $s.guard.h 2 200
    Blade $g @(-176, 0, -160, 4.5, -120, 7, 26, 8) $s.metal $mat
    $cup = EllipsePath 0 34 24 12
    Fill $g $cup (Radial $cup $s.guard.h $s.guard.d -6 30)
    Outline $g $cup $s.guard.o 4
    CrossGuard $g 30 60 8 $s.guard
    Gem $g 0 34 6 $s.gem
}

# Копьё: древко с кольцами и обмоткой, втулка и листовидный наконечник с ребром.
$script:Melee['spear'] = {
    param($g, $s, $mat)
    Shaft $g -64 170 8 $s.wood
    Grip $g 40 104 9.5 $s.leather $s.wrap
    Ring $g 30 10 7 $s.metal
    Ring $g 114 10 7 $s.metal
    $butt = PolyPath @(-9, 158, 9, 158, 7, 174, -7, 174)
    MetalShape $g $butt 9 $s.metal 3.5
    $socket = PolyPath @(-7, -84, 7, -84, 11, -56, -11, -56)
    MetalShape $g $socket 11 $s.metal 3.5
    Ring $g -60 11.5 6 $s.metal
    Blade $g @(-176, 0, -158, 10, -132, 21, -110, 20, -94, 12, -82, 7) $s.metal $mat
}

# Шестопёр: древко, рукоять в обмотке, головка с перьями, шип на конце.
$script:Melee['flanged_mace'] = {
    param($g, $s, $mat)
    Shaft $g -44 154 9 $s.wood
    Grip $g 94 158 10.5 $s.leather $s.wrap
    $knob = EllipsePath 0 166 12 10
    MetalShape $g $knob 12 $s.metal 3.5
    $m = $s.metal
    # Боковые перья: зубчатые пластины слева и справа.
    foreach ($side in -1, 1) {
        $fin = PolyPath @(($side * 14), -150, ($side * 34), -138, ($side * 40), -116, ($side * 30), -104, ($side * 40), -92, ($side * 34), -70, ($side * 14), -56)
        Fill $g $fin (Grad 0 -150 0 -56 @($m.l, $m.h, $m.m, $m.d) @(0, 0.3, 0.6, 1))
        Outline $g $fin $m.o 4
        Line $g ($side * 18) -140 ($side * 34) -116 $m.h 2 180
    }
    # Сердцевина и переднее перо по центру.
    $core = RectPath -16 -152 16 -52 6
    MetalShape $g $core 16 $m
    $front = PolyPath @(-6, -150, 6, -150, 8, -100, 6, -54, -6, -54, -8, -100)
    Fill $g $front (Grad -8 0 8 0 @($m.h, $m.l, $m.m))
    Outline $g $front $m.d 2.5
    $spike = PolyPath @(-9, -150, 0, -176, 9, -150)
    MetalShape $g $spike 9 $m 3.5
    Ring $g -50 13 8 $m
}

# Боевой молот: древко с металлическими полосами, головка с плоским бойком и клювом, шип сверху.
$script:Melee['warhammer'] = {
    param($g, $s, $mat)
    Shaft $g -60 160 9 $s.wood
    $m = $s.metal
    foreach ($side in -1, 1) {
        $langet = RectPath ($side * 9 - 3) -72 ($side * 9 + 3) 6 2
        MetalShape $g $langet 3 $m 2.5
        foreach ($y in -60, -30, 0) { $rivet = EllipsePath ($side * 9) $y 3 3; Fill $g $rivet (Solid $m.h); Outline $g $rivet $m.o 1.5 }
    }
    Grip $g 100 160 10.5 $s.leather $s.wrap
    $cap = EllipsePath 0 166 11 9
    MetalShape $g $cap 11 $m 3.5
    $spike = PolyPath @(-9, -110, 0, -160, 9, -110)
    MetalShape $g $spike 9 $m 3.5
    # Клюв справа.
    $beak = CurvePath @(14, -110, 40, -100, 60, -86, 40, -84, 14, -72) 0.3
    Fill $g $beak (Grad 0 -110 0 -72 @($m.h, $m.l, $m.m, $m.d) @(0, 0.3, 0.6, 1))
    Outline $g $beak $m.o 4
    # Боёк слева: плоская ударная грань с насечкой (холст позволяет |x| + |y| ≤ 176).
    $face = RectPath -54 -112 -16 -68 5
    MetalShape $g $face 20 $m
    $plate = RectPath -58 -114 -48 -66 3
    Fill $g $plate (Grad 0 -114 0 -66 @($m.h, $m.l, $m.m, $m.d) @(0, 0.3, 0.6, 1))
    Outline $g $plate $m.o 3.5
    foreach ($y in -104, -94, -84, -74) { Line $g -57 $y -49 ($y + 4) $m.d 1.6 220 }
    $neck = RectPath -18 -116 18 -64 6
    MetalShape $g $neck 18 $m
    Line $g -10 -108 -10 -72 $m.h 2 200
    $gemSpot = if ($mat -eq 'damascus') { 'ruby' } elseif ($mat -eq 'steel') { 'sapphire' } else { '' }
    if ($gemSpot) { Gem $g 0 -90 7 $gemSpot }
}

# Топор: древко, широкий полукруглый клинок слева и обух справа.
$script:Melee['axe'] = {
    param($g, $s, $mat)
    Shaft $g -146 166 9 $s.wood
    Grip $g 110 162 10.5 $s.leather $s.wrap
    $m = $s.metal
    # Полумесяц клинка слева (холст позволяет |x| + |y| ≤ 176).
    $head = CurvePath @(-8, -138, -22, -140, -40, -134, -60, -114, -70, -94, -64, -70, -46, -50, -26, -58, -14, -74, -8, -84) 0.45
    Fill $g $head (Grad -70 0 -8 0 @($m.h, $m.l, $m.m, $m.d) @(0, 0.25, 0.6, 1))
    $g.SetClip($head)
    if ($mat -eq 'damascus') { Damascus $g -150 -40 70 }
    Curve $g @(-36, -132, -56, -112, -65, -94, -60, -72, -44, -54) $m.h 5 230
    Curve $g @(-28, -124, -46, -108, -54, -92, -50, -74, -38, -62) $m.d 2 140
    $g.ResetClip()
    Outline $g $head $m.o 4.5
    $poll = RectPath 6 -136 24 -88 5
    MetalShape $g $poll 10 $m
    $collar = RectPath -11 -144 11 -80 4
    MetalShape $g $collar 11 $m
    foreach ($y in -128, -96) { $rivet = EllipsePath 0 $y 3.5 3.5; Fill $g $rivet (Solid $m.h); Outline $g $rivet $m.o 1.5 }
}

# Мушкет: ствол с мушкой, цевьё с кольцами, замок с курком, спусковая скоба и приклад.
$script:Melee['musket'] = {
    param($g, $s, $mat)
    $m = $script:Metal.iron
    $wood = $script:Wood
    # Приклад: расширяется к затыльнику (справа — нижняя сторона ружья).
    $stock = PolyPath @(-9, 30, 12, 30, 16, 70, 36, 122, 36, 136, -14, 136, -12, 92)
    Fill $g $stock (Grad -14 0 36 0 @($wood.l, $wood.m, $wood.d) @(0, 0.45, 1))
    $g.SetClip($stock)
    foreach ($k in 0..4) { Curve $g @((-8 + $k * 9), 34, (-6 + $k * 10), 80, (-8 + $k * 10), 134) $wood.d 1.5 150 }
    $g.ResetClip()
    Outline $g $stock $wood.o 4
    $buttPlate = RectPath -15 130 37 140 3
    MetalShape $g $buttPlate 26 $m 3
    # Цевьё под стволом.
    $fore = RectPath 0 -112 15 34 5
    Fill $g $fore (Grad 0 0 15 0 @($wood.l, $wood.m, $wood.d))
    Outline $g $fore $wood.o 3.5
    # Ствол.
    $barrel = RectPath -7 -170 7 34 4
    Fill $g $barrel (Grad -7 0 7 0 @($m.l, $m.h, $m.m, $m.d, $m.o) @(0, 0.25, 0.55, 0.85, 1))
    Outline $g $barrel $m.o 3.5
    $muzzle = RectPath -9 -172 9 -164 2
    MetalShape $g $muzzle 9 $m 3
    $sight = PolyPath @(-7, -158, -14, -154, -7, -150)
    Fill $g $sight (Solid $script:Brass.m); Outline $g $sight $script:Brass.o 2
    foreach ($y in -96, -50, -4) { $band = RectPath -9 ($y - 4) 17 ($y + 4) 2; MetalShape $g $band 13 $script:Brass 3 }
    # Замок и курок.
    $lock = RectPath -6 32 14 66 4
    MetalShape $g $lock 10 $m 3.5
    $hammer = CurvePath @(-6, 40, -18, 34, -24, 42, -16, 50, -6, 52) 0.4
    MetalShape $g $hammer 8 $m 3
    $pan = EllipsePath -8 58 5 4
    Fill $g $pan (Solid $script:Brass.l); Outline $g $pan $script:Brass.o 2
    # Спусковая скоба и крючок.
    Curve $g @(14, 70, 28, 74, 30, 88, 20, 98, 14, 96) $script:Brass.o 7
    Curve $g @(14, 70, 28, 74, 30, 88, 20, 98, 14, 96) $script:Brass.l 3.5
    Curve $g @(14, 78, 20, 82, 19, 88) $m.d 3
}

function RenderMelee([string]$type, [string]$material) {
    $canvas = NewCanvas 45
    $bmp = $canvas[0]; $g = $canvas[1]
    & $script:Melee[$type] $g (Style $material) $material
    $g.Dispose()
    $bmp
}
