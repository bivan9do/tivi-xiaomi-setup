Add-Type -AssemblyName System.Drawing
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$W = 640; $H = 360
$bmp = New-Object System.Drawing.Bitmap $W, $H
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = 'AntiAlias'; $g.TextRenderingHint = 'AntiAliasGridFit'
$g.Clear([System.Drawing.Color]::FromArgb(255, 110, 158, 87))
$white = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 244, 244, 240))
$asphalt = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 90, 94, 100))
# An L-shaped yard road with white edges, a stop line and a parking bay.
foreach ($pair in @(@($white, 6), @($asphalt, 0))) {
  $b = $pair[0]; $e = $pair[1]
  $g.FillRectangle($b, 60 - $e, 150 - $e, 520 + 2 * $e, 90 + 2 * $e)
  $g.FillRectangle($b, 470 - $e, 150 - $e, 90 + 2 * $e, 210 + 2 * $e)
  $g.FillRectangle($b, 250 - $e, 240 - $e, 70 + 2 * $e, 100 + 2 * $e)
}
$g.FillRectangle($white, 420, 150, 10, 90)
for ($x = 70; $x -lt 400; $x += 60) { $g.FillRectangle((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 242, 194, 48))), $x, 192, 30, 6) }
# The car, seen from above, heading right.
$g.FillRectangle((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 17, 17, 17))), 175, 160, 22, 70)
$g.FillRectangle((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 17, 17, 17))), 285, 160, 22, 70)
$g.FillRectangle((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 47, 128, 237))), 150, 168, 190, 54)
$g.FillRectangle((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 31, 95, 175))), 185, 172, 110, 46)
$g.FillRectangle((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 169, 214, 245))), 270, 174, 24, 42)
$font = New-Object System.Drawing.Font 'Segoe UI', 46, ([System.Drawing.FontStyle]::Bold)
$small = New-Object System.Drawing.Font 'Segoe UI', 22, ([System.Drawing.FontStyle]::Bold)
$fmt = New-Object System.Drawing.StringFormat; $fmt.Alignment = 'Center'
$shadow = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(150, 0, 0, 0))
$g.DrawString('Sa hình B', $font, $shadow, (New-Object System.Drawing.RectangleF 3, 23, $W, 80), $fmt)
$g.DrawString('Sa hình B', $font, $white, (New-Object System.Drawing.RectangleF 0, 20, $W, 80), $fmt)
$g.DrawString('11 bài thi · số sàn', $small, $white, (New-Object System.Drawing.RectangleF 0, 92, $W, 40), $fmt)
$g.Dispose()
$bmp.Save((Join-Path $root 'app\src\main\res\drawable-xhdpi\banner.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
'banner ok'
