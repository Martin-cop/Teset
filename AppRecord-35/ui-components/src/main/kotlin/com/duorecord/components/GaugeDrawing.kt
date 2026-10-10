package com.duorecord.components

import android.graphics.*

/** Platform-only reusable geometry: 0 battery, 1/4 thermometer, 3/5 storage. */
object GaugeDrawing {
        fun draw(c:Canvas,p:Paint,b:RectF,index:Int,count:Int,fill:Float=count/10f,graduated:Boolean=false,chargePulse:Float=0f) {
            val unit=b.height()/14f
            p.shader=null;p.clearShadowLayer();p.strokeCap=Paint.Cap.BUTT
            if(index in setOf(1,4)) { drawThermometer(c,p,b,fill,unit,if(index==1)60 else 100,graduated);return }
            if(index in setOf(3,5)) { drawBarrel(c,p,b,fill,unit);return }
            p.style=Paint.Style.STROKE;p.strokeWidth=unit.coerceAtLeast(1f);p.color=0xffffd52a.toInt()
            val body=RectF(b.left,b.top,b.right-2*unit,b.bottom)
            c.drawRoundRect(body,2*unit,2*unit,p)
            // Contact side is half its previous height; outward thickness is tripled.
            p.style=Paint.Style.FILL
            val terminal=RectF(body.right-unit*.6f,b.centerY()-2.16f*unit,b.right+unit*.26f,b.centerY()+2.16f*unit)
            val radius=unit*.7f
            val terminalPath=Path().apply { addRoundRect(terminal,floatArrayOf(0f,0f,radius,radius,radius,radius,0f,0f),Path.Direction.CW) }
            c.drawPath(terminalPath,p)
            for(n in 0..9) {
                p.color=Color.rgb(73+(130*chargePulse).toInt(),40+(22*chargePulse).toInt(),40+(15*chargePulse).toInt())
                val step=(body.width()-4*unit)/10
                val cell=RectF(body.left+2*unit+n*step,b.top+2*unit,body.left+2*unit+(n+1)*step-unit*.6f,b.bottom-2*unit)
                c.drawRoundRect(cell,unit*.4f,unit*.4f,p)
                // One integer percent is one invisible tenth of a cell: 73% = 7 + 0.3 cells.
                val portion=(fill*10-n).coerceIn(0f,1f)
                if(portion>0f) {
                    val saved=c.save();c.clipRect(cell.left,cell.top,cell.left+cell.width()*portion,cell.bottom)
                    p.color=0xff65ff99.toInt();c.drawRoundRect(cell,unit*.4f,unit*.4f,p);c.restoreToCount(saved)
                }
            }
        }
        private fun drawThermometer(c:Canvas,p:Paint,b:RectF,fill:Float,u:Float,maximum:Int,numbers:Boolean) {
            val radius=b.height()*.30f;val cx=b.left+radius;val cy=b.centerY();val half=2.2f*u
            val bulb=Path().apply { addCircle(cx,cy,radius,Path.Direction.CW) }
            val tube=Path().apply { addRoundRect(RectF(cx,cy-half,b.right-u*.5f,cy+half),half*.90f,half*.90f,Path.Direction.CW) }
            val outline=Path().apply { op(bulb,tube,Path.Op.UNION) }
            p.style=Paint.Style.FILL;p.color=0xff15191c.toInt();c.drawPath(outline,p)
            p.style=Paint.Style.STROKE;p.strokeWidth=(u*.65f).coerceAtLeast(.7f);p.color=0xffffd52a.toInt();c.drawPath(outline,p)
            val start=cx+radius+u;val end=b.right-2*u
            p.style=Paint.Style.FILL;p.color=0xff492828.toInt();c.drawCircle(cx,cy,radius-1.4f*u,p);c.drawRect(cx,cy-u,end,cy+u,p)
            if(fill>0) {
                p.color=0xffff3434.toInt();c.drawCircle(cx,cy,radius-1.4f*u,p)
                // A square meniscus follows the exact temperature, not rounded ten-step buckets.
                c.drawRect(cx,cy-u,start+(end-start)*fill,cy+u,p)
            }
            p.textAlign=Paint.Align.CENTER;p.typeface=Typeface.create("sans-serif",Typeface.NORMAL)
            p.textSize=b.height()*.13f
            for(degree in 0..maximum step 2) {
                val x=start+(end-start)*degree/maximum
                p.color=0x99ffffff.toInt();p.strokeWidth=(if(degree%10==0)u*.20f else u*.09f).coerceAtLeast(.45f)
                c.drawLine(x,cy-half+u*.5f,x,cy,p)
            }
            if(numbers)for(degree in 0..maximum step 10) {
                val x=(start+(end-start)*degree/maximum).coerceIn(start+u,end-u)
                val ty=cy+half*.50f
                p.color=Color.WHITE;p.style=Paint.Style.FILL
                p.setShadowLayer(u*.45f,0f,0f,Color.BLACK)
                // Numerals occupy the clear half of the tube, away from the ticks.
                c.drawText(degree.toString(),x,ty-(p.ascent()+p.descent())/2,p)
                p.clearShadowLayer()
            }
        }

        private fun drawBarrel(c:Canvas,p:Paint,original:RectF,fill:Float,u:Float) {
            val b=RectF(original).apply { inset(0f,height()*.16f) }
            val rim=(b.height()*.22f).coerceAtMost(b.width()*.12f)
            val body=Path().apply {
                moveTo(b.left+rim,b.top);lineTo(b.right-rim,b.top)
                cubicTo(b.right+rim*.33f,b.top,b.right+rim*.33f,b.bottom,b.right-rim,b.bottom)
                lineTo(b.left+rim,b.bottom)
                cubicTo(b.left-rim*.33f,b.bottom,b.left-rim*.33f,b.top,b.left+rim,b.top);close()
            }
            p.style=Paint.Style.FILL;p.shader=LinearGradient(0f,b.top,0f,b.bottom,intArrayOf(0xff5d666c.toInt(),0xff252d33.toInt(),0xff10151a.toInt()),floatArrayOf(0f,.4f,1f),Shader.TileMode.CLAMP)
            c.drawPath(body,p)
            val saved=c.save();c.clipPath(body)
            p.shader=LinearGradient(0f,b.top,0f,b.bottom,intArrayOf(0xffb2ffd1.toInt(),0xff51d18a.toInt(),0xff125231.toInt()),floatArrayOf(0f,.45f,1f),Shader.TileMode.CLAMP)
            val boundary=b.left+rim+(b.width()-rim*2)*fill
            if(fill>=1f)c.drawRect(b,p) else if(fill>0f) {
                c.drawRect(b.left,b.top,boundary,b.bottom,p)
                c.drawOval(RectF(boundary-rim,b.top,boundary+rim,b.bottom),p)
            }
            p.shader=null;p.style=Paint.Style.STROKE;p.strokeWidth=(u*.28f).coerceAtLeast(1f);p.color=0x668ce5b0
            for(n in 1..19) {
                val x=b.left+rim+(b.width()-2*rim)*n/20
                p.strokeWidth=(if(n%5==0)u*.3f else u*.14f).coerceAtLeast(.6f)
                p.color=if(n%5==0)0xeeffffff.toInt() else 0x99ffffff.toInt()
                c.drawArc(RectF(x-rim,b.top,x+rim,b.bottom),-90f,180f,false,p)
            }
            if(fill>0 && fill<1) { p.strokeWidth=u*.4f;p.color=0xffa5f1ca.toInt();c.drawArc(RectF(boundary-rim,b.top,boundary+rim,b.bottom),-90f,180f,false,p) }
            c.restoreToCount(saved)
            p.style=Paint.Style.STROKE;p.strokeWidth=(u*.65f).coerceAtLeast(1f);p.color=0xffffd52a.toInt();c.drawPath(body,p)
            p.style=Paint.Style.FILL;p.color=if(fill>0)0xff22583c.toInt() else 0xff242d33.toInt()
            c.drawOval(RectF(b.left,b.top,b.left+rim*2,b.bottom),p)
            p.style=Paint.Style.STROKE;p.color=0xffffd52a.toInt();c.drawOval(RectF(b.left,b.top,b.left+rim*2,b.bottom),p)
            c.drawArc(RectF(b.right-rim*2,b.top,b.right,b.bottom),90f,180f,false,p)
            p.shader=null
        }
}
