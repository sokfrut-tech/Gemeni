package com.jarvis.breeno.assistant.provider
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
class BreenoBridgeProvider:ContentProvider(){ override fun onCreate()=true; override fun query(u:Uri,p:Array<out String>?,s:String?,a:Array<out String>?,o:String?):Cursor?=null; override fun getType(u:Uri)=null; override fun insert(u:Uri,v:ContentValues?)=null; override fun delete(u:Uri,s:String?,a:Array<out String>?)=0; override fun update(u:Uri,v:ContentValues?,s:String?,a:Array<out String>?)=0 }
