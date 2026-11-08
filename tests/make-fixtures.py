from PIL import Image, ImageDraw, ImageFont
from pathlib import Path
import subprocess, math
root=Path(__file__).resolve().parents[1]/'build'/'fixtures'
root.mkdir(parents=True,exist_ok=True)
font='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
tracks=[('01 Sunset Drive','After Hours',(222,92,44)),('02 Blue Hour','After Hours',(222,92,44)),('03 Somewhere Slow','Slow Sunday',(79,128,115)),('04 Golden','Slow Sunday',(79,128,115)),('05 Moonlit','Daydream',(94,89,160)),('06 Soft Focus','Daydream',(94,89,160))]
for i,(title,album,rgb) in enumerate(tracks):
 cover=root/(album+'.png')
 if not cover.exists():
  im=Image.new('RGB',(512,512)); px=im.load()
  for y in range(512):
   for x in range(512):
    glow=max(0,1-math.hypot(x-330,y-170)/500)
    px[x,y]=tuple(int(c*(.25+.75*glow)) for c in rgb)
  d=ImageDraw.Draw(im)
  for j in range(5):
   pad=65+j*25; d.ellipse((pad,pad,512-pad,512-pad),outline=tuple(min(255,c+25*j) for c in rgb),width=2)
  d.text((34,390),album.upper(),fill='#fff5e8',font=ImageFont.truetype(font,30))
  d.text((36,440),'STUDIO SESSIONS',fill='#e0c7b0',font=ImageFont.truetype(font,14))
  im.save(cover)
 subprocess.run(['ffmpeg','-v','error','-y','-f','lavfi','-i',f'sine=frequency={220+i*55}:duration=18','-i',str(cover),'-map','0:a','-map','1:v','-c:a','libmp3lame','-q:a','6','-af','volume=0.02','-c:v','mjpeg','-id3v2_version','3','-metadata',f'title={title[3:]}','-metadata','artist=Studio Sessions','-metadata',f'album={album}','-metadata',f'track={i+1}','-metadata:s:v','title=Album cover','-metadata:s:v','comment=Cover (front)',str(root/(title+'.mp3'))],check=True)
