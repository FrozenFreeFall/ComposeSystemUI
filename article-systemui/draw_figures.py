from PIL import Image, ImageDraw, ImageFont
from pathlib import Path
import math

OUT = Path(__file__).parent / 'images'
OUT.mkdir(exist_ok=True)
F = 'C:/Windows/Fonts/msyh.ttc'
FB = 'C:/Windows/Fonts/msyhbd.ttc'
def font(n, bold=False): return ImageFont.truetype(FB if bold else F, n)
INK='#182945'; MUTED='#52667e'; BLUE='#2967c9'; TEAL='#16897c'; ORANGE='#dc8243'; LINE='#cbd7e6'; BG='#f4f7fc'; WHITE='#ffffff'

def canvas(kicker, title, sub, height=850):
 im=Image.new('RGB',(1600,height),BG); d=ImageDraw.Draw(im)
 d.rounded_rectangle((50,40,1550,height-40),radius=32,fill=WHITE,outline='#e2eaf4',width=2)
 d.text((95,78),kicker,font=font(23,True),fill=BLUE)
 d.text((95,118),title,font=font(49,True),fill=INK)
 d.text((97,192),sub,font=font(25),fill=MUTED)
 return im,d

def txt(d,xy,s,size=28,color=INK,bold=False): d.text(xy,s,font=font(size,bold),fill=color)
def box(d,xy,title,lines,accent=BLUE,fill='#f5f8fe'):
 x1,y1,x2,y2=xy; d.rounded_rectangle(xy,20,fill=fill,outline=LINE,width=2)
 d.rounded_rectangle((x1,y1,x1+9,y2),4,fill=accent)
 txt(d,(x1+27,y1+23),title,30,INK,True)
 for i,line in enumerate(lines): txt(d,(x1+27,y1+76+i*39),line,23,MUTED)
def arrow(d,p1,p2,color=BLUE,width=5):
 d.line([p1,p2],fill=color,width=width); a=math.atan2(p2[1]-p1[1],p2[0]-p1[0]); l=16
 pts=[p2,(p2[0]-l*math.cos(a-0.5),p2[1]-l*math.sin(a-0.5)),(p2[0]-l*math.cos(a+0.5),p2[1]-l*math.sin(a+0.5))]; d.polygon(pts,fill=color)
def save(im,name): im.save(OUT / name,optimize=True)

im,d=canvas('01 / 全局视角','SystemUI 是系统交互的“前台”','画界面、接系统状态、响应用户；系统服务负责管理和协调。')
box(d,(100,300,480,492),'应用 / Launcher',['提交通知、请求系统栏外观','部分设备由桌面展示最近任务'],TEAL,'#eef9f7')
box(d,(610,300,990,492),'系统服务',['WMS：管理窗口与 Insets','通知、任务等服务管理各自数据'],BLUE)
box(d,(1120,300,1500,492),'SystemUI',['状态栏、导航栏、通知面板','监听状态并响应交互'],ORANGE,'#fff7f0')
arrow(d,(487,396),(600,396),TEAL); arrow(d,(997,396),(1110,396),BLUE)
box(d,(100,595,1500,716),'一条理解主线',['数据来自系统服务 → SystemUI 更新界面 → 用户操作交回系统或应用处理'],BLUE,'#f3f7fe')
save(im,'01-overview.png')

im,d=canvas('02 / 窗口与 INSETS','窗口的大小，不等于应用必须避让的范围','Insets 是系统提供的区域信息；应用可以选择避让，也可以绘制到后面。',900)
d.rounded_rectangle((125,290,620,760),32,fill='#f1f5fb',outline=LINE,width=3)
d.rounded_rectangle((150,310,595,735),12,fill='#e8f4f2'); txt(d,(210,515),'应用背景与内容',34,TEAL,True)
d.rounded_rectangle((150,310,595,378),12,fill='#c9e2f7'); txt(d,(180,325),'状态栏图标叠在背景上',26,BLUE,True)
txt(d,(165,387),'应用背景可以延伸到顶部',20,TEAL)
box(d,(745,300,1485,470),'窗口高度',['这个窗口实际占多大的矩形区域','可以因为动画或布局需要而改变'],BLUE)
box(d,(745,515,1485,685),'状态栏 Insets',['告知应用状态栏所占的区域与可见性','应用决定给内容留边，或让背景延伸进去'],TEAL,'#eef9f7')
arrow(d,(620,346),(735,385),BLUE); arrow(d,(620,405),(735,585),TEAL)
save(im,'02-window-insets.png')

im,d=canvas('03 / 外观传递','应用希望深色图标，SystemUI 是怎么知道的？','以状态栏图标外观为例；实际实现会区分显示器、区域、栏模式等。')
steps=[('应用窗口',['请求浅色背景外观','LIGHT_STATUS_BARS']),('窗口管理',['结合窗口和显示策略','确定系统栏属性']),('状态栏服务',['通过 Binder','转发系统栏命令']),('SystemUI',['CommandQueue 分发','主线程更新图标'])]
for i,(title,lines) in enumerate(steps):
 x=100+i*365; box(d,(x,320,x+320,515),title,lines,[TEAL,BLUE,BLUE,ORANGE][i])
 if i<3: arrow(d,(x+324,418),(x+355,418),MUTED,4)
d.rounded_rectangle((100,605,1500,727),18,fill='#edf7f5'); txt(d,(135,627),'记住：LIGHT 描述的是适合浅色背景的外观，通常意味着深色图标。',27,TEAL,True)
save(im,'03-appearance-flow.png')

im,d=canvas('04 / 下拉面板','下拉面板：一次手势，经过三个步骤','下面是项目中的简化流程，原生 SystemUI 的布局与手势处理更复杂。')
steps=[('① 识别拖动',['从本项目状态栏向下滑','达到手势条件后开始']),('② 更新可见范围',['拖动时调整面板裁剪区域','界面跟随手指逐步露出']),('③ 松手后决定',['快速滑动看速度；慢速看距离','展开或收起，并管理焦点'])]
for i,(title,lines) in enumerate(steps):
 x=105+i*500; box(d,(x,315,x+420,522),title,lines,[BLUE,TEAL,ORANGE][i])
 if i<2: arrow(d,(x+425,418),(x+490,418),MUTED,4)
d.rounded_rectangle((105,615,1495,727),18,fill='#fff6ed'); txt(d,(135,637),'普通版只处理从自己的悬浮状态栏开始的手势，不接管原生状态栏。',27,ORANGE,True)
save(im,'04-shade-gesture.png')

im,d=canvas('05 / 通知','通知像收件箱：先管理数据，再决定怎么展示','新增、更新、移除都来自系统；不同展示位置有各自的规则。',940)
box(d,(95,305,425,500),'应用',['提交通知、内容和动作','例如聊天消息'],TEAL,'#eef9f7')
box(d,(505,305,855,500),'通知管理服务',['管理通知与渠道','检查相关权限和设置'],BLUE)
box(d,(935,305,1505,500),'SystemUI',['监听通知变化；过滤、排序、分组','展示列表、图标、横幅或锁屏内容'],ORANGE,'#fff7f0')
arrow(d,(430,404),(495,404),MUTED,4); arrow(d,(860,404),(925,404),MUTED,4)
box(d,(95,590,1505,760),'用户操作',['点击：执行通知提供的动作     ｜     清除：请求移除允许清除的通知','收起面板或横幅消失，不代表通知已被清除'],BLUE,'#f4f7fe')
arrow(d,(1220,505),(1220,580),BLUE)
save(im,'05-notifications.png')

im,d=canvas('06 / 最近任务','最近任务记录的是任务，不是“正在运行的进程”','下面是常见的 AOSP 分工；厂商 ROM 和本项目的实现可能不同。')
box(d,(100,310,490,505),'SystemUI',['接收最近任务入口','把操作交给对应组件'],BLUE)
box(d,(605,310,995,505),'Launcher / Quickstep',['展示任务卡片','处理手势与动画'],TEAL,'#eef9f7')
box(d,(1110,310,1500,505),'系统任务服务',['维护任务与快照','处理切换、移除'],ORANGE,'#fff7f0')
arrow(d,(495,408),(595,408),MUTED,4); arrow(d,(1000,408),(1100,408),MUTED,4)
d.rounded_rectangle((100,610,1500,730),18,fill='#edf7f5'); txt(d,(130,632),'卡片可能保留已不在内存中的任务；移除卡片也不等于强制停止应用。',27,TEAL,True)
save(im,'06-recents.png')
print('Generated:', ', '.join(p.name for p in sorted(OUT.glob('*.png'))))
