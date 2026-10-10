---
name: morteza-android-neon-ui
description: Reproduce Morteza's exact Android neon interface from Video to Text version 56. Use when requested to apply this template to another Android app, or create matching buttons, status labels, cards, dropdowns, tabs, input fields, settings, help or About pages.
---

# قالب رابط کاربری اندروید مرتضی

## قرارداد استفاده
این فایل مستقل و قابل انتقال است. آن را کامل بخوان و برای هر جزء جدید از مشخصات و کد مرجع انتهای همین فایل استفاده کن. مرجع، سورس Video to Text نسخهٔ ۵۶ است. طرح را از روی خاطره یا توصیف کلی «نئون» بازسازی نکن. ظاهر را مستقل از منطق تبدیل ویدیو پیاده‌سازی کن؛ شبکه، مدل‌های صوتی، نام پکیج قبلی و محدودیت زمانی نسخهٔ قبلی را منتقل نکن.
در پروژهٔ Java/View کد رسم مرجع را با تغییر نام package استفاده کن. در Kotlin/View همان کد را تبدیل کن. در Compose هندسه، ترتیب رسم، شعاع سایه و رنگ‌های همان مرجع را حفظ کن؛ از Button، Card و Dropdown پیش‌فرض Material به عنوان ظاهر نهایی استفاده نکن. از تبدیل خودکار تمام اندازه‌ها به مقادیر Material خودداری کن.

## رنگ‌های قطعی
- پس‌زمینهٔ کل صفحه: #4C4C4C.
- داخل باکس اصلی: #212121.
- نوار عنوان و خط ضخیم دور باکس اصلی: #050607.
- زمینهٔ کامبوباکس و قاب ورودی و کلید: #1E2429.
- سطح دکمه و داخل ورودی URL: #000000؛ یکدست، بدون گرادیان.
- دور کنترل‌ها: #777B7E؛ دور دکمه: #697076؛ حالت فشردهٔ دور دکمه: #557766.
- نوشتهٔ عمومی: #EEEEEE؛ عنوان برنامه: سفید.
- نوشتهٔ انتخاب‌شده، ورودی URL و روشن بودن کلید: #65FF99.
- hint ورودی: #A0A0A0.
- وضعیت عادی: #42BFFF؛ خطا: #FF5555.
- نور متن زرد: #FFD22C؛ رنگ روی متن: #FFE978؛ زرد آیکون: #FFDF58.
- نور متن در فشار: #44FF80؛ رنگ روی متن در فشار: #AEFFCB.
- نور باکس اصلی: #00BFFF سپس #42BFFF.
- نور آبی دکمه: ARGB #8838CFFF سپس #6642BFFF؛ آلفا را حذف نکن.
- خط زیر عنوان باکس‌های اصلی: #343638.
- زمینهٔ تب فعال: #343D43؛ تب غیر فعال: #050607؛ متن غیر فعال: #CCCCCC.
- آیکون خروجی غیر فعال: #70777B.

## واحد، چینش و تراز
همهٔ اندازه‌های هندسی dp هستند؛ مگر وقتی کد رسم مرجع صریحاً متن را با density رسم می‌کند. متن TextView معمولاً sp است. اندازهٔ متن Canvas در SignButton برابر ۱۳×density است؛ آن را برای شباهت دقیق با sp عوض نکن. صفحهٔ اصلی ScrollView با fillViewport دارد. ریشه عمودی است، با padding چپ و راست ۱۰ و بالا و پایین ۱۲. چیدمان ریشهٔ مرجع LTR است؛ متن فارسی داخل بدنه RTL شود و URL همیشه LTR بماند. والدهای نورپردازی clipChildren و clipToPadding را false کنند تا سایه قطع نشود. نور، زیر سطح شکل رسم شود؛ نه یک خط رنگی روی لبه.
محتوای باکس‌ها را روی دو خط عمودی مشترک تراز کن. ردیف‌های اصلی margin افقی ۱۲ دارند. فاصلهٔ ردیف‌های داخل باکس ۱۲ است؛ ردیف انتخاب فایل زیر تب فاصلهٔ ۱۶ دارد. فاصلهٔ بین باکس‌های صفحهٔ اصلی ۱۰ است. باکس پایین padding برابر ۲۴ دارد؛ ارتفاع محتوا نباید این فاصله را بپوشاند. ارتفاع معمول کنترل‌ها ۴۸ است. برای صفحه‌های فرعی همان زمینه، باکس، نور، متن و دکمه را تکرار کن.

## باکس اصلی و نوار عنوان
- از GlowPanel مرجع استفاده کن: مستطیل رسم با inset ده از چهار طرف، شعاع گوشه ۱۷، زمینه #212121.
- لایهٔ اول سایه شعاع ۱۰ و offset صفر، رنگ #00BFFF؛ لایهٔ دوم شعاع ۵ و offset صفر، رنگ #42BFFF. نور چهارطرفه و متقارن است.
- خط دور مشکی با ضخامت ۴، پس از سایه رسم شود. سایه جای خط دور را نگیرد.
- layout باکس: padding چپ/بالا/راست ۱۲، پایین ۲۴، orientation عمودی.
- عنوان وسط با متن ۱۶sp، bold، #EEEEEE، includeFontPadding=false؛ padding عنوان ۱۰،۳،۱۰،۷. نوار #050607، گوشه‌های بالا ۱۴ و پایین قائم.
- زیر نوار خط پیوستهٔ ۲dp #343638، margin افقی منفی ۲، تا اتصال به دو کناره. برای وضعیت بدون عنوان، نوار خالی نساز.

## دکمهٔ استاندارد
از SignButton انتهای فایل استفاده کن؛ پس‌زمینه و foreground و stateListAnimator و elevation پیش‌فرض را حذف کن، نرم‌افزاری رسم کن. سطح همیشه مشکی یکدست، شعاع ۷؛ خط باریک ۱dp برای برجستگی.
ارتفاع layout معمول ۴۸، عرض عمودی match-parent و در ردیف افقی weight مساوی است. فاصلهٔ افقی عمومی دو دکمه ۸؛ فاصلهٔ دکمه‌های پایین صفحه ۲ است.
متن در مرکز افقی و عمودی با ascent/descent، MONOSPACE BOLD، uppercase، اندازهٔ ۱۳×density؛ اگر جا نشد تدریجی تا ۹×density کم شود. زرد، با دو سایهٔ متن شعاع ۶ و ۲؛ در فشار سبز با شعاع ۸ و ۲. متن روشن روی سایه رسم شود.
درون باکس، نور دکمه زرد با شعاع ۳، offset صفر؛ هنگام فشار سبز. بیرون باکس و برای کنترل انتخاب فایل/دکمه‌های تب URL از blueUnderlight=true استفاده کن: شعاع ۴، offset صفر، #8838CFFF و لایهٔ دوم شعاع ۲ #6642BFFF. شدت نور دکمه از باکس اصلی کمتر است. به جای حدس بر اساس parent، نقش دکمه را صریح تعیین کن.
ظاهر فشار لحظه‌ای را با تب منتخب دائمی اشتباه نگیر. دکمهٔ disabled باید واقعاً غیرقابل کلیک و دارای contentDescription باشد. کد نسخهٔ ۵۶ متن دکمهٔ عمومی disabled را کامل خاموش نمی‌کند؛ این نقص را به پروژهٔ جدید منتقل نکن: رنگ متن disabled را خاکستری کن و سایهٔ نوشتهٔ آن را حذف کن. آیکون‌های خروجی در مرجع این تمایز را دارند.

## ردیف بالای صفحه
عنوان برنامه سفید و بدون پنل مستقل؛ در مرجع ImageView با FIT_CENTER، ارتفاع ۴۴، وزن ۱، padding چهارطرف ۱۲ است. برای پروژهٔ جدید متن نام همان برنامه را استفاده کن؛ لوگوی Video to Text را به برنامهٔ دیگر منتقل نکن. اگر عنوان تصویری شد، contentDescription نام برنامه را حفظ کن. فونت عنوان مرجع دارایی تصویری است؛ ادعای فونت SONY نکن.
سه دکمه در سمت راست به ترتیب راهنما، درباره، تنظیمات: اندازهٔ ۵۰×۵۰، فاصلهٔ چپ ۲؛ face inset شش از چهارطرف، نور آبی شعاع ۲. علامت راهنما ؟، درباره i داخل دایره با شعاع ۱۱، تنظیمات چرخ‌دندهٔ واقعی Canvas با هشت دندانه، شعاع بیرونی ۱۱، داخلی ۹ و حفرهٔ وسط ۴. برای تکرار دقیق از کد مرجع استفاده کن و به emoji سیستم متکی نباش.

## باکس وضعیت بالای صفحه
از PulseStatusLabel داخل GlowPanel بدون عنوان استفاده کن. متن دوخطی، ۱۴sp، وسط افقی و عمودی، includeFontPadding=false، حداقل ارتفاع ۴۸؛ margin چپ و راست ۱۲. بعد از هر تغییر متن Gravity.CENTER را دوباره اعمال و requestLayout کن. سطر اول مرحلهٔ کار؛ سطر دوم کیفیت/درصد/زمان. وضعیت عادی آبی و خطا قرمز باشد؛ نوع خطا را از state واقعی دریافت کن، به تشخیص انگلیسی با جستجوی متن متکی نشو. ellipsize انتهایی مرجع را فقط برای فضای محدود حفظ کن؛ جزئیات کامل خطا باید قابل دسترسی باشد.
هنگام پردازش فقط نوشته با ValueAnimator از آلفای .35 تا 1، مدت ۹۰۰ms، REVERSE بی‌نهایت چشمک بزند. خط نورانی اضافه زیر نوشته رسم نکن. پایان، توقف یا جداشدن View انیمیشن را لغو کند.
ProgressBar زیر نوشته، ارتفاع ۸، margin افقی ۱۲ و بالا ۸، زرد #FFDF58، max=100؛ فقط هنگام کار دیده شود، درصد نامعلوم indeterminate. پایان شامل زمان صرف‌شده، مدت ورودی، تعداد حروف و اندازه/تعداد خروجی موجود باشد؛ اطلاعات ناموجود حذف شود. پیام موقت بدون نابود کردن آخرین وضعیت پردازش نمایش و سپس وضعیت جاری بازگردانده شود.

## کامبوباکس و کشوی گزینه‌ها
ردیف ۴۸dp، padding ۷،۵،۷،۵؛ حاشیهٔ افقی ۱۲. GlowOutline: inset سه، شعاع گوشه ۱۰، زمینه #1E2429، سایه سبز شعاع ۳ و offset صفر در چهار طرف، خط خاکستری ضخامت ۲. child پس‌زمینه شفاف باشد تا خط دور یکنواخت بماند.
ستون label ثابت ۱۱۰dp، متن ۱۵sp وسط، #EEEEEE؛ ستون مقدار وزن ۱، padding افقی ۶، تراز LEFT|CENTER_VERTICAL، includeFontPadding=false، سبز. ستون فلش ۲۰dp وسط با ▾؛ کلیک فلش همان کشو را باز کند. همهٔ مقادیر انتخاب‌شده روی یک خط عمودی مشترک شروع شوند.
کشو StyledSpinner: popup زمینه #080A0B، شعاع ۱۲، دور ۲ خاکستری، عرض حداقل ۱۶۰dp یا عرض انتخابگر. هر گزینه جدا در FrameLayout با padding افقی ۸ و عمودی ۵؛ متن ۱۷sp bold وسط، minHeight=42، padding ۸،۵،۸،۵. گزینهٔ عادی گوشه ۱۰، خط ۱ خاکستری و زمینه #080A0B؛ متن زرد #FFE978 با نور #FFD22C شعاع۳. منتخب سبز. فشار زمینه #274F39 و خط سبز. فاصلهٔ گزینه‌ها را با wrapper نگه دار؛ کادرها به هم نچسبند.

## URL و ورودی متن
قاب URL هم‌ارتفاع Mode یعنی ۴۸dp، زمینه #1E2429، شعاع۱۲، خط۲ #777B7E. padding قاب ۷،۸،۱۲،۸؛ عنوان «URL :» سفید ۱۶sp وسط عمودی. فاصلهٔ بعد از عنوان ۸dp. ارتفاع تکست‌باکس ۳۰dp، وزن۱، margin راست۲. برای زیاد کردن حاشیه، ارتفاع input را کم کن؛ قاب را بلندتر نکن.
input مشکی یکدست؛ گوشه‌های چپ قائم و راست شعاع۱۰؛ متن سبز۱۶sp، hint خاکستری #A0A0A0، LTR و singleLine، LEFT|CENTER_VERTICAL؛ padding چپ۶ و راست۳۲. ضربدر سبز × اندازه۲۴sp، عرض۲۸dp و ارتفاعmatch-parent، راست، وسط افقی و عمودی، includeFontPadding=false. field نباید خط بیرونی قاب را بپوشاند.
تاریخچهٔ مقادیر معتبر قبلی را مستقل از طراحی ذخیره و هنگام ورود نشان بده؛ hint متناسب با کاربرد پروژه باشد، نه نام سرویس‌های برنامهٔ مرجع.

## تب‌ها و کلید روشن/خاموش
تب‌ها دکمهٔ عملیاتی نیستند. ۸۴×۳۶dp، متن۱۶sp؛ فعال زمینه #343D43 و متن bold سبز، غیر فعال زمینه #050607 و متن #CCCCCC؛ متن همیشه وسط. خط سبز زیر تب‌ها حذف شده است. فاصلهٔ label عنوان Source تا تب اول۱۴dp؛ بار padding۸،۲،۸،۲. در برنامهٔ دیگر نام تب متناسب با کاربرد عوض شود.
کلید Switch در قاب۴۸dp با padding۸،۶،۸،۶، زمینه #1E2429، شعاع۱۰ و دور۲. متن عمومی سفید؛ کلمات ON/OFF روی کلید ننویس. thumb روشن#65FF99، خاموش#9A9A9A؛ track روشن#277A49، خاموش#555555؛ دور روشن سبز، دور خاموش خاکستری. وضعیت از جابه‌جایی thumb و رنگ مشخص باشد، accessibility state نیز درج شود.

## ردیف آیکون خروجی
این الگو اختیاری و وابسته به خروجی برنامه است. در مرجع زیر انتخاب فایل/کنترل URL داخل Source چهار آیکون پوشه، ویدیو، صدا، متن در یک ردیف ثابت هستند. ارتفاع container۵۸ و دکمه۵۴، عرض weight=1 مساوی، margin چپ/راست۲؛ نام فایل داخل آنها نیست. همه از ابتدا دیده ولی غیرفعال‌اند، آیکون خاکستری؛ پس از پایان، فقط موارد واقعاً موجود فعال و زرد می‌شوند. نور دور آبی. ورودی جدید وضعیت قبلی را پاک کند. با کلیک متن همیشه همان نمایشگر داخلی باز شود؛ Back به صفحهٔ اصلی برگردد. پوشه باید دقیقاً محل خروجی جاری را نشان دهد؛ picker مجوز فولدر را به‌عنوان باز کردن پوشه جا نزن. کد هندسهٔ چهار آیکون در SignButton مرجع هست.

## درباره و هویت عمومی توسعه‌دهنده
اطلاعات تأییدشدهٔ مرجع را دقیق درج کن:
Developed by Morteza
rassoly@gmail.com
New York
نام خانوادگی یا مشخصات تماس اضافی حدس نزن. نام برنامه و Version از پروژهٔ جاری/PackageInfo گرفته شود. سال۲۰۲۶ مرجع ثابت عمومی نیست؛ فقط سال واقعی همان انتشار یا مقدار صریح پروژه درج شود.
Development: زبان و SDK واقعی پروژه؛ Native processing: فقط اگر واقعاً دارد؛ Project: محیط و ابزار واقعی ساخت. مرجع Java / Android SDK، C/C++، Android Studio / Gradle است، نه Kotlin/Compose؛ در پروژهٔ جدید اطلاعات نادرست را کپی نکن.
صفحهٔ مرجع About زمینه #4C4C4C، layout عمودی با Gravity.CENTER، padding۲۰؛ متن۱۷sp زرد#FFDF58 در قاب outline زمینه#1E2429، شعاع۱۲، دور۲ خاکستری، ترازوسط، padding۷،۵،۷،۵. دکمه Error details و ردیف Update/Back با همان SignButton هستند. اگر پروژه جزئیات خطا یا به‌روزرسانی ندارد آن دکمه حذف شود. محدودیت Update before مرجع را منتقل نکن. Guide تکراری داخل About اضافه نکن؛ راهنما آیکون مستقل دارد.
برای ظاهر عمومی هماهنگ‌تر، در صورت درخواست «About مثل باکس اصلی» محتوای بالا را داخل GlowPanel با عنوان About سفید قرار بده؛ این یک انتخاب مشخص است، نه ظاهر دقیق About نسخه۵۶. بدون درخواست جدید همین تفاوت را خودسرانه اصلاح نکن.

## صفحات راهنما، تنظیمات و خروجی
همان پس‌زمینه و GlowPanel، عنوان مشکی با متن سفید، بدنه سفید۱۷sp با padding افقی۱۶/عمودی۱۲، محتوای بلند ScrollView. متن سفید روی زمینهٔ تیره باید واقعاً در layout وزن/ارتفاع مناسب داشته باشد؛ صفحهٔ خالی را موفق تلقی نکن. Back/Close و بک گوشی به صفحهٔ قبلی داخلی برگردند. درباره و راهنما متفاوت‌اند؛ متن راهنما متناسب با قابلیت‌های برنامهٔ جدید نوشته شود.
هنگام کار در صفحهٔ اصلی بمان؛ Start غیرفعال و Stop فعال دیده شود. تنظیمات عمومی بازشدنی بمانند؛ تنظیمات مؤثر بر عملیات جاری را قفل یا برای اجرای بعدی اعمال کن. رفتار عملیات را از ظاهر کنترل‌ها جدا نگه دار.

## کنترل نتیجه
کد را کامپایل کن و screenshot واقعی صفحهٔ اصلی، کشو، حالت فشار، خطا، پردازش، کلید و About را در صورت امکان مقایسه کن. نبود تست گوشی را صریح اعلام کن؛ تضمین برابری پیکسلی بین دستگاه‌ها نده. نور قطع‌شده، ضخامت دوگانهٔ قاب، جابه‌جایی ستون مقدار، دکمه‌های چسبیده و متن نامرئی را خطا بدان. dp/sp و تنظیم اندازهٔ فونت سیستم بر خروجی اثر دارند. کد زیر مرجع رسم است؛ وقتی توضیح کلی مبهم شد اعداد و ترتیب رسم آن ملاک‌اند، جز اصلاح صریح disabled و state خطا که بالا تعیین شده‌اند.

## کدهای مرجع مستقل
برای استفاده، هر بلوک را در فایل هم‌نام قرار بده و package را به پکیج برنامهٔ جدید تغییر بده. کلاس‌های package-private باید در همان پکیج مصرف‌کننده باشند. این قطعات منطق شبکه/تبدیل ندارند. PulseStatusLabel به StatusMessages وابسته است؛ در پروژهٔ جدید حالت خطا را صریح به آن بده.

### GlowPanel.java

```java
package com.morteza.reelsave;
import android.content.Context;import android.graphics.*;import android.widget.LinearLayout;
/** Black continuous border with a soft underlight. Content never has an inset title border. */
final class GlowPanel extends LinearLayout {
 final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final float d;
 GlowPanel(Context c){super(c);d=getResources().getDisplayMetrics().density;setWillNotDraw(false);setLayerType(LAYER_TYPE_SOFTWARE,null);setBackgroundColor(Color.TRANSPARENT);}
 @Override protected void onDraw(Canvas canvas){RectF r=new RectF(10*d,10*d,getWidth()-10*d,getHeight()-10*d);paint.setStyle(Paint.Style.FILL);paint.setColor(0xff212121);paint.setShadowLayer(10*d,0,0,0xff00bfff);canvas.drawRoundRect(r,17*d,17*d,paint);paint.setShadowLayer(5*d,0,0,0xff42bfff);canvas.drawRoundRect(r,17*d,17*d,paint);paint.clearShadowLayer();paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(4*d);paint.setColor(0xff050607);canvas.drawRoundRect(r,17*d,17*d,paint);super.onDraw(canvas);}
}

```

### GlowOutline.java

```java
package com.morteza.reelsave;
import android.graphics.*;import android.graphics.drawable.Drawable;
final class GlowOutline extends Drawable {
 final Paint p=new Paint(3);final float d;GlowOutline(float density){d=density;}
 public void draw(Canvas c){Rect b=getBounds();RectF r=new RectF(b.left+3*d,b.top+3*d,b.right-3*d,b.bottom-3*d);p.setColor(0xff1e2429);p.setStyle(Paint.Style.FILL);p.setShadowLayer(3*d,0,0,0xff65ff99);c.drawRoundRect(r,10*d,10*d,p);p.clearShadowLayer();p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2*d);p.setColor(0xff777b7e);c.drawRoundRect(r,10*d,10*d,p);p.setStyle(Paint.Style.FILL);}
 public void setAlpha(int a){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}

```

### SignButton.java

```java
package com.morteza.reelsave;
import android.content.Context;import android.graphics.*;import android.widget.Button;
/** Rounded black sign with a white underlight, green illuminated pressed letters. */
public final class SignButton extends Button {
 int outputIcon=0;boolean iconMode=false,blueUnderlight=false;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final float density;
 public SignButton(Context context){super(context);density=getResources().getDisplayMetrics().density;setBackground(null);setBackgroundTintList(null);setForeground(null);setStateListAnimator(null);setElevation(0);setForceDarkAllowed(false);setLayerType(LAYER_TYPE_SOFTWARE,null);setAllCaps(false);setPadding(0,0,0,0);setMinWidth(0);setMinimumWidth(0);setMinHeight(0);setMinimumHeight(0);}
 @Override public void setSelected(boolean value){super.setSelected(value);invalidate();}
 @Override protected void drawableStateChanged(){super.drawableStateChanged();invalidate();}
 @Override protected void onDraw(Canvas c){float d=density;boolean active=isSelected()||isPressed();float shift=active?2*d:0;float inset=iconMode?6*d:blueUnderlight?6*d:5*d;RectF face=new RectF(inset,(iconMode?6*d:blueUnderlight?6*d:4*d)+shift,getWidth()-inset,getHeight()-(iconMode?6*d:blueUnderlight?6*d:6*d)+shift);paint.setStyle(Paint.Style.FILL);paint.setShader(null);paint.clearShadowLayer();
 paint.setColor(Color.BLACK);paint.setShadowLayer((iconMode?2f:blueUnderlight?4f:3)*d,0,0,(iconMode||blueUnderlight)?0x8838cfff:(active?0xff44ff80:0xffffd22c));c.drawRoundRect(face,7*d,7*d,paint);if(blueUnderlight){paint.setShadowLayer(2*d,0,0,0x6642bfff);c.drawRoundRect(face,7*d,7*d,paint);}paint.clearShadowLayer();paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(d);paint.setColor(active?0xff557766:0xff697076);c.drawRoundRect(face,7*d,7*d,paint);paint.setStyle(Paint.Style.FILL);
 if(outputIcon>0){float cx=face.centerX(),cy=face.centerY();paint.setColor(isEnabled()?(active?0xff65ff99:0xffffdf58):0xff70777b);if(isEnabled())paint.setShadowLayer(2*d,0,0,paint.getColor());paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2*d);if(outputIcon==1){Path folder=new Path();folder.moveTo(cx-11*d,cy+8*d);folder.lineTo(cx-11*d,cy-8*d);folder.lineTo(cx-3*d,cy-8*d);folder.lineTo(cx,cy-4*d);folder.lineTo(cx+11*d,cy-4*d);folder.lineTo(cx+11*d,cy+8*d);folder.close();c.drawPath(folder,paint);}else if(outputIcon==2){c.drawRoundRect(new RectF(cx-11*d,cy-8*d,cx+11*d,cy+8*d),2*d,2*d,paint);Path play=new Path();play.moveTo(cx-3*d,cy-4*d);play.lineTo(cx+4*d,cy);play.lineTo(cx-3*d,cy+4*d);play.close();c.drawPath(play,paint);}else if(outputIcon==3){c.drawLine(cx+5*d,cy+5*d,cx+5*d,cy-9*d,paint);c.drawLine(cx+5*d,cy-9*d,cx-3*d,cy-6*d,paint);paint.setStyle(Paint.Style.FILL);c.drawOval(new RectF(cx-3*d,cy+2*d,cx+5*d,cy+8*d),paint);}else{c.drawRoundRect(new RectF(cx-8*d,cy-10*d,cx+8*d,cy+10*d),2*d,2*d,paint);for(int j=-1;j<=1;j++)c.drawLine(cx-4*d,cy+j*5*d,cx+4*d,cy+j*5*d,paint);}paint.clearShadowLayer();paint.setStyle(Paint.Style.FILL);return;}
 if(iconMode&&getText().toString().equals("ⓘ")){float cx=face.centerX(),cy=face.centerY();paint.setColor(active?0xff65ff99:0xffffdf58);paint.setShadowLayer(2*d,0,0,paint.getColor());paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2*d);c.drawCircle(cx,cy,11*d,paint);paint.setStyle(Paint.Style.FILL);c.drawCircle(cx,cy-6*d,1.5f*d,paint);c.drawRoundRect(new RectF(cx-1.4f*d,cy-2*d,cx+1.4f*d,cy+7*d),d,d,paint);paint.clearShadowLayer();return;}if(iconMode&&getText().toString().equals("⚙")){float cx=face.centerX(),cy=face.centerY(),outer=11*d;Path gear=new Path();for(int i=0;i<64;i++){double angle=i*Math.PI/32;float radius=(i%8>=2&&i%8<=5)?outer:9*d;float xx=cx+(float)Math.cos(angle)*radius,yy=cy+(float)Math.sin(angle)*radius;if(i==0)gear.moveTo(xx,yy);else gear.lineTo(xx,yy);}gear.close();gear.addCircle(cx,cy,4*d,Path.Direction.CCW);gear.setFillType(Path.FillType.EVEN_ODD);paint.setColor(active?0xff65ff99:0xffffdf58);paint.setShadowLayer(2*d,0,0,paint.getColor());c.drawPath(gear,paint);paint.clearShadowLayer();return;}
 String text=getText().toString().toUpperCase(java.util.Locale.US);paint.setTypeface(Typeface.create(Typeface.MONOSPACE,Typeface.BOLD));float size=(iconMode?22:13)*d;paint.setTextSize(size);while(paint.measureText(text)>face.width()-(iconMode?2:8)*d&&size>9*d){size-=.5f*d;paint.setTextSize(size);}float x=(getWidth()-paint.measureText(text))/2,y=face.centerY()-(paint.ascent()+paint.descent())/2;Path letters=new Path();paint.getTextPath(text,0,text.length(),x,y,letters);int glow=active?0xff44ff80:0xffffd22c;paint.setColor(isEnabled()?glow:0xff887b53);paint.setShadowLayer((active?8:6)*d,0,0,glow);c.drawPath(letters,paint);paint.setShadowLayer(2*d,0,0,glow);c.drawPath(letters,paint);paint.clearShadowLayer();paint.setColor(active?0xffaeffcb:0xffffe978);c.drawPath(letters,paint);
 }
}

```

### StyledSpinner.java

```java
package com.morteza.reelsave;
import android.content.Context;import android.widget.*;import android.view.*;import android.graphics.drawable.GradientDrawable;
final class StyledSpinner extends Spinner {
 StyledSpinner(Context c){super(c);}
 @Override public boolean performClick(){if(!isEnabled())return false;ListPopupWindow popup=new ListPopupWindow(getContext());popup.setAnchorView(this);popup.setModal(true);float d=getResources().getDisplayMetrics().density;GradientDrawable bg=new GradientDrawable();bg.setColor(0xff080a0b);bg.setCornerRadius(12*d);bg.setStroke(Math.round(2*d),0xff777b7e);popup.setBackgroundDrawable(bg);popup.setWidth(Math.max(getWidth(),Math.round(160*d)));popup.setAdapter(new BaseAdapter(){public int getCount(){return StyledSpinner.this.getAdapter().getCount();}public Object getItem(int n){return StyledSpinner.this.getAdapter().getItem(n);}public long getItemId(int n){return n;}public View getView(int n,View old,ViewGroup parent){TextView b=new TextView(getContext());b.setText(String.valueOf(getItem(n)));boolean selected=n==getSelectedItemPosition();b.setTextColor(selected?0xff65ff99:0xffffe978);b.setTextSize(17);b.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);b.setGravity(Gravity.CENTER);b.setPadding(Math.round(8*d),Math.round(5*d),Math.round(8*d),Math.round(5*d));b.setMinHeight(Math.round(42*d));b.setShadowLayer(3*d,0,0,selected?0xff65ff99:0xffffd22c);android.graphics.drawable.StateListDrawable states=new android.graphics.drawable.StateListDrawable();android.graphics.drawable.GradientDrawable pressed=new android.graphics.drawable.GradientDrawable();pressed.setColor(0xff274f39);pressed.setCornerRadius(10*d);states.addState(new int[]{android.R.attr.state_pressed},pressed);android.graphics.drawable.GradientDrawable normal=new android.graphics.drawable.GradientDrawable();normal.setColor(0xff080a0b);normal.setCornerRadius(10*d);normal.setStroke(Math.round(d),0xff777b7e);pressed.setStroke(Math.round(d),0xff65ff99);states.addState(new int[]{},normal);b.setElevation(2*d);b.setShadowLayer(3*d,0,0,selected?0xff65ff99:0xffffd22c);b.setBackground(states);b.setOnClickListener(v->{setSelection(n);popup.dismiss();});FrameLayout row=new FrameLayout(getContext());row.setPadding(Math.round(8*d),Math.round(5*d),Math.round(8*d),Math.round(5*d));row.addView(b,new FrameLayout.LayoutParams(-1,-2));return row;}});popup.show();return true;}
}

```

### SourceTab.java

```java
package com.morteza.reelsave;
import android.content.Context;
import android.graphics.*;
import android.widget.Button;
/** Persistent source selection, independent of transient action-button state. */
final class SourceTab extends Button {
 final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final float d;
 SourceTab(Context c){super(c);d=getResources().getDisplayMetrics().density;setBackground(null);setAllCaps(false);setPadding(0,0,0,0);setMinWidth(0);setMinHeight(0);setStateListAnimator(null);setContentDescription("Source tab");}
 @Override public void setSelected(boolean selected){super.setSelected(selected);setContentDescription(getText()+" tab, "+(selected?"selected":"not selected"));invalidate();}
 @Override protected void onDraw(Canvas c){boolean active=isSelected();p.setColor(active?0xff343d43:0xff050607);c.drawRect(0,0,getWidth(),getHeight(),p);p.setColor(active?0xff65ff99:0xffcccccc);p.setTypeface(Typeface.create(Typeface.DEFAULT,active?Typeface.BOLD:Typeface.NORMAL));p.setTextSize(16*getResources().getDisplayMetrics().scaledDensity);String t=getText().toString();c.drawText(t,(getWidth()-p.measureText(t))/2,(getHeight()-p.ascent()-p.descent())/2,p);}
}

```

### PulseStatusLabel.java

```java
package com.morteza.reelsave;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;

import android.widget.TextView;
final class PulseStatusLabel extends TextView {
 private ValueAnimator animator;private float strength;private final float density;
 PulseStatusLabel(Context context){super(context);density=getResources().getDisplayMetrics().density;setLayerType(LAYER_TYPE_SOFTWARE,null);}
 @Override public void setText(CharSequence text,BufferType type){setGravity(android.view.Gravity.CENTER);setIncludeFontPadding(false);setTextColor(StatusMessages.error(text)?0xffff5555:0xff42bfff);super.setText(text,type);requestLayout();}
 void setWorking(boolean on){if(on&&animator==null){animator=ValueAnimator.ofFloat(.35f,1f);animator.setDuration(900);animator.setRepeatMode(ValueAnimator.REVERSE);animator.setRepeatCount(ValueAnimator.INFINITE);animator.addUpdateListener(a->{strength=(Float)a.getAnimatedValue();invalidate();});animator.start();}else if(!on&&animator!=null){animator.cancel();animator=null;strength=0;invalidate();}}
 @Override protected void onDraw(Canvas canvas){if(animator==null){super.onDraw(canvas);return;}int saved=canvas.saveLayerAlpha(0,0,getWidth(),getHeight(),Math.round(strength*255));super.onDraw(canvas);canvas.restoreToCount(saved);}
 @Override protected void onDetachedFromWindow(){setWorking(false);super.onDetachedFromWindow();}
}

```

### StatusMessages.java

```java
package com.morteza.reelsave;
final class StatusMessages {static boolean error(CharSequence value){if(value==null)return false;String text=value.toString().toLowerCase(java.util.Locale.US).replaceAll("\\b0 failed\\b|\\bfailed\\s*[:=]?\\s*0\\b","");return text.matches("(?s).*(failed|error|could not|cannot|unable to|not enough|needs at least|invalid|unsupported|unavailable|no clear speech|no audio track|no post links|not found|insufficient).* ".trim());}}

```

### ProgressText.java

```java
package com.morteza.reelsave;
final class ProgressText {
 static String twoLines(String value){String text=value.replace('\n',' ');java.util.regex.Matcher mode=java.util.regex.Pattern.compile("\\s*•\\s*(Fast|High|Ultra)\\b").matcher(text);if(mode.find())return text.substring(0,mode.start()).trim()+"\n"+text.substring(mode.start()).replaceFirst("^\\s*•\\s*","");int detail=text.indexOf(" • ");if(detail>=0)return text.substring(0,detail)+"\n"+text.substring(detail+3);java.util.regex.Matcher percent=java.util.regex.Pattern.compile("\\s+\\d+%").matcher(text);if(percent.find())return text.substring(0,percent.start())+"\n"+text.substring(percent.start()).trim();return text;}
}

```
