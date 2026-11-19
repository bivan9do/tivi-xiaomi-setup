Add-Type -AssemblyName System.Drawing
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$sites = @(
  @{ f = 'vnexpress'; name = 'VnExpress'; sub = 'vnexpress.net'; bg = @(160, 30, 70); fg = @(255, 255, 255) },
  @{ f = 'tuoitre';   name = 'Tuổi Trẻ';  sub = 'tuoitre.vn';    bg = @(0, 82, 155);  fg = @(255, 255, 255) },
  @{ f = 'dantri';    name = 'Dân Trí';   sub = 'dantri.com.vn'; bg = @(0, 120, 70);  fg = @(255, 255, 255) },
  @{ f = 'thanhnien'; name = 'Thanh Niên'; sub = 'thanhnien.vn'; bg = @(20, 20, 24);  fg = @(255, 255, 255) }
)
foreach ($s in $sites) {
  $dir = Join-Path $root "app\src\$($s.f)\res\drawable-xhdpi"
  New-Item -ItemType Directory -Force $dir | Out-Null
  $W = 640; $H = 360
  $bmp = New-Object System.Drawing.Bitmap $W, $H
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.SmoothingMode = 'AntiAlias'; $g.TextRenderingHint = 'AntiAliasGridFit'
  $bg = [System.Drawing.Color]::FromArgb(255, $s.bg[0], $s.bg[1], $s.bg[2])
  $g.Clear($bg)
  $fg = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, $s.fg[0], $s.fg[1], $s.fg[2]))
  $dim = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(200, 255, 255, 255))
  $big = New-Object System.Drawing.Font 'Segoe UI', 64, ([System.Drawing.FontStyle]::Bold)
  $small = New-Object System.Drawing.Font 'Segoe UI', 22
  $fmt = New-Object System.Drawing.StringFormat; $fmt.Alignment = 'Center'; $fmt.LineAlignment = 'Center'
  $g.DrawString($s.name, $big, $fg, (New-Object System.Drawing.RectangleF 0, 70, $W, 150), $fmt)
  $g.DrawString("Tin tức · $($s.sub)", $small, $dim, (New-Object System.Drawing.RectangleF 0, 230, $W, 60), $fmt)
  $g.Dispose()
  $bmp.Save((Join-Path $dir 'banner.png'), [System.Drawing.Imaging.ImageFormat]::Png)
  $bmp.Dispose()
  "banner $($s.f)"
}
