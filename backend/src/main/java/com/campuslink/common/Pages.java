package com.campuslink.common;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.*;
public final class Pages {
 private Pages() {}
 public static int size(int size){return Math.min(50,Math.max(1,size));}
 public static <T> Page<T> request(int page,int size){return Page.of(Math.max(1,page),size(size));}
 public static Map<String,Object> of(Page<?> p,Object records){return Map.of("records",records,"total",p.getTotal(),"page",p.getCurrent(),"size",p.getSize());}
 public static Map<String,Object> slice(List<?> list,int page,int size){int n=size(size),p=Math.max(1,page);int start=(int)Math.min((long)(p-1)*n,list.size());return Map.of("records",list.subList(start,Math.min(start+n,list.size())),"total",list.size(),"page",p,"size",n);}
}
