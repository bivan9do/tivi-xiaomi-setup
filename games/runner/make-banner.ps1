Add-Type -AssemblyName System.Drawing
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$W = 640; $H = 360
$bmp = New-Object System.Drawing.Bitmap $W, $H
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = 'AntiAlias'; $g.TextRenderingHint = 'AntiAliasGridFit'
$sky = New-Object System.Drawing.Drawing2D.LinearGradientBrush ((New-Object System.Drawing.Point 0, 0), (New-Object System.Drawing.Point 0, 150), [System.Drawing.Color]::FromArgb(255, 91, 184, 240), [System.Drawing.Color]::FromArgb(255, 207, 234, 247))
$g.FillRectangle($sky, 0, 0, $W, 150)
$g.FillRectangle((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 122, 154, 90))), 0, 150, $W, 210)
# Track bed narrowing to the horizon, with rails.
$bed = [System.Drawing.Point[]]@((New-Object System.Drawing.Point 250, 150), (New-Object System.Drawing.Point 390, 150), (New-Object System.Drawing.Point 640, 360), (New-Object System.Drawing.Point 0, 360))
$g.FillPolygon((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 140, 128, 115))), $bed)
$rail = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255, 220, 224, 228)), 5
foreach ($x in @(-0.33, -0.13, 0.13, 0.33)) { $g.DrawLine($rail, 320 + $x * 140, 150, 320 + $x * 640 * 1.4, 360) }
# A train front in the left lane.
$g.FillRectangle((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 142, 59, 70))), 60, 175, 120, 150)
$g.FillRectangle((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 28, 40, 51))), 76, 192, 88, 55)
$lamp = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 255, 245, 157))
$g.FillEllipse($lamp, 78, 275, 18, 18); $g.FillEllipse($lamp, 144, 275, 18, 18)
# Coins in the right lane.
$coin = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 255, 200, 40))
$g.FillEllipse($coin, 470, 300, 34, 34); $g.FillEllipse($coin, 440, 255, 26, 26); $g.FillEllipse($coin, 418, 222, 20, 20)
$white = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::White)
$shadow = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(160, 0, 0, 0))
$font = New-Object System.Drawing.Font 'Segoe UI', 50, ([System.Drawing.FontStyle]::Bold)
$fmt = New-Object System.Drawing.StringFormat; $fmt.Alignment = 'Center'
$g.DrawString('Lướt đường ray', $font, $shadow, (New-Object System.Drawing.RectangleF 3, 33, $W, 110), $fmt)
$g.DrawString('Lướt đường ray', $font, $white, (New-Object System.Drawing.RectangleF 0, 30, $W, 110), $fmt)
$g.Dispose()
$bmp.Save((Join-Path $root 'app\src\main\res\drawable-xhdpi\banner.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
'banner ok'
