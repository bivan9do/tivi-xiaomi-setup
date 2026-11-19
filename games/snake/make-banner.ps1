Add-Type -AssemblyName System.Drawing
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$W = 640; $H = 360
$bmp = New-Object System.Drawing.Bitmap $W, $H
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = 'AntiAlias'; $g.TextRenderingHint = 'AntiAliasGridFit'
$g.Clear([System.Drawing.Color]::FromArgb(255, 20, 44, 30))
# A checker band like the board, then the snake curling towards an apple.
$alt = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 28, 56, 38))
for ($x = 0; $x -lt 16; $x++) { for ($y = 0; $y -lt 9; $y++) { if (($x + $y) % 2) { $g.FillRectangle($alt, $x * 40, $y * 40, 40, 40) } } }
$body = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 123, 214, 123))
$head = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 184, 242, 138))
$cells = @(@(2,6),@(3,6),@(4,6),@(5,6),@(6,6),@(6,5),@(7,5),@(8,5),@(9,5),@(10,5))
foreach ($c in $cells) { $g.FillRectangle($body, $c[0] * 40 + 3, $c[1] * 40 + 3, 34, 34) }
$g.FillRectangle($head, 10 * 40 + 3, 5 * 40 + 3, 34, 34)
$eye = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 16, 32, 15))
$g.FillEllipse($eye, 428, 208, 7, 7); $g.FillEllipse($eye, 428, 222, 7, 7)
$g.FillEllipse((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 242, 92, 84))), 525, 205, 30, 30)
$g.FillEllipse((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 76, 175, 80))), 540, 195, 13, 9)
$white = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::White)
$font = New-Object System.Drawing.Font 'Segoe UI', 54, ([System.Drawing.FontStyle]::Bold)
$fmt = New-Object System.Drawing.StringFormat; $fmt.Alignment = 'Center'
$g.DrawString('Rắn săn mồi', $font, $white, (New-Object System.Drawing.RectangleF 0, 40, $W, 110), $fmt)
$g.Dispose()
$bmp.Save((Join-Path $root 'app\src\main\res\drawable-xhdpi\banner.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
'banner ok'
