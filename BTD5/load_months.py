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
def loadDc(y,m,d):
    x=urllib.request.urlopen(urllib.request.Request("http://topper64.co.uk/nk/btd5/dc/"+str(y)+"/"+str(m)+"/"+str(d),method="POST",headers={"Referer":"http://topper64.co.uk/nk/btd5/dc/"+str(y)+"/"+str(m)+"/"+str(d),"x-is-ajax":"true","Host":"topper64.co.uk","Origin":"http://topper64.co.uk"}),data=bytes("q=&format=json&year="+str(y)+"&month="+str(m)+"&day="+str(d)+"&get=Get%20Info",encoding="utf-8")).read().decode()
    e=None
    n=parseString(x).childNodes[0].childNodes[0].data
    q=json.loads(n)
    q["objectType"]="challenges.ChallengeDef"
    if(m<10):
        m="0"+str(m)
    if(d<10):
        d="0"+str(d)
    
    q["date"]=""+str(y)+str(m)+str(d)
    q["description"]=q["description"].replace("/b","\\/b")
    if("towers" in q.keys()):
        q["towers"]={"objectType":"Vector.<String>","data":q["towers"]}
    else:
        q["towers"]={"objectType":"Vector.<String>","data":[]}
    if("agents" in q.keys()):
        q["agents"]={"objectType":"Vector.<String>","data":q["agents"]}
    else:
        q["agents"]={"objectType":"Vector.<String>","data":[]}
    if("bloonLevelOffsets" in q.keys()):
        q["bloonLevelOffsets"]={"objectType":"Vector.<int>","data":q["bloonLevelOffsets"]};
    a={"objectType":"Vector.<profile.InventoryCount>","data":[]};
    #older format has a list
    if("reserves" in q.keys() and not (isinstance(q["reserves"] ,list))):
        for t in q["reserves"].keys():
            a["data"].append({"objectType":"profile.InventoryCount","id":t,"count":q["reserves"].get(t)})
    elif("reserves" in q.keys()):
        a["data"]=q["reserves"]
    else:
        a["data"]=[]
    q["reserves"]=a;
    u={"objectType":"Vector.<challenges.UpgradeLock>","data":[]}
    if("upgradeLocks" in q.keys() and not (isinstance(q["upgradeLocks"] ,list))):
        for t in q["upgradeLocks"].keys():
            u["data"].append({"objectType":"challenges.UpgradeLock","id":t,"path":0,"upgrade":q["upgradeLocks"].get(t)[0]})
            u["data"].append({"objectType":"challenges.UpgradeLock","id":t,"path":1,"upgrade":q["upgradeLocks"].get(t)[1]})
    elif("upgradeLocks" in q.keys()):
        u["data"]=q["upgradeLocks"]
    else:
        u["data"]=[]
    q["upgradeLocks"]=u;
    #print(q)
    e=base64.b64encode((bytes(json.dumps(q),encoding="utf-8"))).decode("utf-8")
    #print(e)
    return e
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
    f=open("./dc/challenges-month-"+str(y)+str(m),"w+",newline="\n")
    while start_date < end_date:
        #print(start_date.strftime("%Y-%m-%d"))
        s=str(start_date.day)
        if(start_date.day<10):
            s="0"+s
        f.write(str(y)+str(m)+s+loadDc(start_date.year,start_date.month,start_date.day))
        if(start_date < end_date - delta):
            f.write("\n")
        start_date += delta
    return y
x=2012
while x<=2018:
    n=1
    while n<=12 and (x!=2018 or n<=6):
        print("LOADING "+str(n)+"/"+str(x))
        loadMonth(x,n)
        n+=1
    x+=1