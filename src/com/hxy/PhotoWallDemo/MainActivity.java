package com.hxy.PhotoWallDemo;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewTreeObserver;
import android.widget.GridView;

public class MainActivity extends Activity {
    private GridView mPhotoWall;
    private PhotoWallAdapter mAdapter;
    private int mImageThumbSize;
    private int mImageThumbSpacing;
    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mImageThumbSize = getResources().getDimensionPixelSize(
                R.dimen.image_thumbnail_size);
        mImageThumbSpacing = getResources().getDimensionPixelSize(
                R.dimen.image_thumbnail_spacing);
        mPhotoWall = (GridView) findViewById(R.id.photo_wall);
        mAdapter = new PhotoWallAdapter(this, 0, Images.imageThumbUrls,
                mPhotoWall);
        mPhotoWall.setAdapter(mAdapter);


        mPhotoWall.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                final int numColumns=(int)Math.floor(mPhotoWall.getWidth()/(mImageThumbSize+mImageThumbSpacing));
                if(numColumns>0){
                    int columnWidth=(mPhotoWall.getWidth()/numColumns-mImageThumbSpacing);
                    mAdapter.setItemHeight(columnWidth);
                    mPhotoWall.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
