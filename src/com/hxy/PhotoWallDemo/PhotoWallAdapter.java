package com.hxy.PhotoWallDemo;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by xianyu.hxy on 2015/8/24.
 */
public class PhotoWallAdapter extends ArrayAdapter<String> {
    private Set<BitmapWorkerTask> taskCollection;
    private LruCache<String,Bitmap> mMemoryCache;
    private DiskLruCache mDiskLruCache;
    private GridView mPhotoWall;
    private int mItemHeight=0;
    public PhotoWallAdapter(Context context,int textViewResourceId,String[] objects,GridView photoWall){
        super(context,textViewResourceId,objects);
        mPhotoWall=photoWall;
        taskCollection=new HashSet<BitmapWorkerTask>();
        int maxMemory=(int)Runtime.getRuntime().maxMemory();
        Log.e("maxMemory",maxMemory+"");
        int cacheSize=maxMemory/8;
        mMemoryCache=new LruCache<String, Bitmap>(cacheSize){
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount();
            }
        };
        File cacheDir=getDiskCacheDir(context,"thumb");
        if(!cacheDir.exists()){
            cacheDir.mkdir();
        }
        try {
            mDiskLruCache=DiskLruCache.open(cacheDir,getAppVersion(context),1,10*1024*1024 );
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addBitmapToMemoryCache(String key,Bitmap bitmap){
        if(getBitmapFromMemoryCache(key)==null){
            mMemoryCache.put(key,bitmap);
        }
    }
    public Bitmap getBitmapFromMemoryCache(String key){
        return mMemoryCache.get(key);
    }
    public void loadBitmaps(ImageView imageView,String imageUrl){
        Bitmap bitmap=getBitmapFromMemoryCache(imageUrl);
        if(bitmap==null){
            BitmapWorkerTask task=new BitmapWorkerTask();
            taskCollection.add(task);
            task.execute(imageUrl);
        }else {
            if(imageView!=null&&bitmap!=null){
                imageView.setImageBitmap(bitmap);
            }
        }
    }
    public void cancenlAllTasks(){
        if(taskCollection!=null){
            for(BitmapWorkerTask task:taskCollection){
                task.cancel(false);
            }
        }
    }
    public File getDiskCacheDir(Context context,String uniqueName){
        String cachePath;
        if(Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())||!Environment.isExternalStorageRemovable()){
            cachePath=context.getExternalCacheDir().getPath();
        }else {
            cachePath=context.getCacheDir().getPath();
        }
        Log.e("cachePath:",cachePath);
        return new File(cachePath+File.separator+uniqueName);
    }
    public int getAppVersion(Context context){
        try {
            PackageInfo info=context.getPackageManager().getPackageInfo(context.getPackageName(),0);
            return info.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return 1;
    }
    public void setItemHeight(int height){
        if(height==mItemHeight)return;
        mItemHeight=height;
        notifyDataSetChanged();
    }
    public String hashKeyForDisk(String key){
        String cacheKey;
        try {
            final MessageDigest mDigest=MessageDigest.getInstance("MD5");
            mDigest.update(key.getBytes());
            cacheKey=bytesToHexString(mDigest.digest());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            cacheKey=String.valueOf(key.hashCode());
        }
        return cacheKey;
    }
    private String bytesToHexString(byte[] bytes){
        StringBuilder sb=new StringBuilder();
        for(int i=0;i<bytes.length;i++){
            String hex=Integer.toHexString(0xff&bytes[i]);
            if(hex.length()==1){
                sb.append('0');
            }
            sb.append(hex);
        }
        Log.e("bytesToHexString","bytes:"+bytes.toString()+",hex:"+sb.toString());
        return sb.toString();
    }
    public void fluchCache(){
        if(mDiskLruCache!=null){
            try {
                mDiskLruCache.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Log.e("view-position:",""+position);
        final String url=getItem(position);
        View view;
        if(convertView==null){
            view= LayoutInflater.from(getContext()).inflate(R.layout.photo_layout,null);
        }else {
            view=convertView;
        }
        final ImageView imageView=(ImageView)view.findViewById(R.id.photo);
        if(imageView.getLayoutParams().height!=mItemHeight){
            imageView.getLayoutParams().height=mItemHeight;
        }
        imageView.setTag(url);
        imageView.setImageResource(R.drawable.empty_photo);
        loadBitmaps(imageView,url);
        return view;
    }

    class BitmapWorkerTask extends AsyncTask<String,Void,Bitmap>{
        private String imageUrl;
        @Override
        protected Bitmap doInBackground(String... params) {
            imageUrl=params[0];
            FileDescriptor fileDescriptor=null;
            FileInputStream fileInputStream=null;
            DiskLruCache.Snapshot snapshot=null;


            try {
                final String key=hashKeyForDisk(imageUrl);
                snapshot=mDiskLruCache.get(key);
                if(snapshot==null){
                    DiskLruCache.Editor editor=mDiskLruCache.edit(key);
                    if(editor!=null){
                        OutputStream outputStream=editor.newOutputStream(0);
                        if(downloadUrlToStream(imageUrl,outputStream)){
                            editor.commit();
                        }else {
                            editor.abort();
                        }
                    }
                    snapshot=mDiskLruCache.get(key);
                }
                if(snapshot!=null){
                    fileInputStream= (FileInputStream) snapshot.getInputStream(0);
                    fileDescriptor=fileInputStream.getFD();
                }
                Bitmap bitmap=null;
                if(fileDescriptor!=null){
                    bitmap= BitmapFactory.decodeFileDescriptor(fileDescriptor);
                }
                if(bitmap!=null){
                    addBitmapToMemoryCache(params[0],bitmap);
                }
                return bitmap;
            } catch (IOException e) {
                e.printStackTrace();
            }finally {
                if(fileDescriptor==null&&fileInputStream!=null){
                    try {
                        fileInputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            return null;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            super.onPostExecute(bitmap);
            ImageView imageView=(ImageView)mPhotoWall.findViewWithTag(imageUrl);
            if(imageView!=null&&bitmap!=null){
                imageView.setImageBitmap(bitmap);
            }
            taskCollection.remove(this);
        }
    }
    private boolean downloadUrlToStream(String urlString,OutputStream outputStream){
        HttpURLConnection urlConnection=null;
        BufferedOutputStream out=null;
        BufferedInputStream in=null;

        try {
            final URL url=new URL(urlString);
            urlConnection=(HttpURLConnection) url.openConnection();
            in=new BufferedInputStream(urlConnection.getInputStream(),8*1024);
            out=new BufferedOutputStream(outputStream,8*1024);
            int b;
            while((b=in.read())!=-1){
                out.write(b);
            }
            return true;
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if(urlConnection!=null){
                urlConnection.disconnect();
            }
            if(out!=null){
                try {
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }if(in!=null){
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return false;
    }

}












































