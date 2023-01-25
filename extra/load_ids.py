# -*- coding: utf-8 -*-
"""
Created on Thu Jul 14 10:39:56 2022

@author: Glenn
"""
#
#note: encryption must be removed from swf for these to load(take utils.§ i§ and make decrypt just return its input)
#so add the following at start of decrypt method pcode:
#getlocal1
#callproperty QName(PackageNamespace(""),"toString"), 0
#returnvalue
import base64
import urllib.request, json
from datetime import date, timedelta
from xml.dom.minidom import parseString
#data = json.loads()
f=open("./dc/challenge-id-archive","w+",newline="\n")
def loadDc(y,m,d):
    x=urllib.request.urlopen(urllib.request.Request("http://topper64.co.uk/nk/btd5/dc/"+str(y)+"/"+str(m)+"/"+str(d),method="POST",headers={"Referer":"http://topper64.co.uk/nk/btd5/dc/"+str(y)+"/"+str(m)+"/"+str(d),"x-is-ajax":"true","Host":"topper64.co.uk","Origin":"http://topper64.co.uk"}),data=bytes("q=&format=json&year="+str(y)+"&month="+str(m)+"&day="+str(d)+"&get=Get%20Info",encoding="utf-8")).read().decode()
    n=parseString(x).childNodes[0].childNodes[0].data
    q=json.loads(n)
    #print(e)
    return q["id"]
def loadMonth(y,m):
    start_date = date(y, m, 1)
    delta = timedelta(days=1)
    if(m==1 and y==2012):
        start_date = date(y, m, 27)
    if(m==12):
        end_date=date(y+1,1,1)
    else:
        end_date=date(y,m+1,1)
    if(m<10):
        m="0"+str(m)
    while start_date < end_date:
        #print(start_date.strftime("%Y-%m-%d"))
        s=str(start_date.day)
        if(start_date.day<10):
            s="0"+s
        f.write(loadDc(start_date.year,start_date.month,start_date.day))
        f.write("\n")
        start_date += delta
    return y
x=2012
while x<=2022:
    n=1
    while n<=12 and (x!=2022 or n<=4):
        print("LOADING "+str(n)+"/"+str(x))
        loadMonth(x,n)
        n+=1
    x+=1
f.close()