/*
*  Copyright (C) 2015-present TzuTaLin
*/

package com.tzutalin.dlibtest;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.media.ExifInterface;
import android.media.FaceDetector;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.widget.Toast;

import com.dexafree.materialList.card.Card;
import com.dexafree.materialList.card.provider.BigImageCardProvider;
import com.dexafree.materialList.view.MaterialListView;
import com.tzutalin.dlib.Constants;
import com.tzutalin.dlib.FaceDet;
import com.tzutalin.dlib.PedestrianDet;
import com.tzutalin.dlib.VisionDetRet;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import hugo.weaving.DebugLog;
import timber.log.Timber;
import java.lang.Math;
import java.lang.Object;
import java.util.Vector;

import static java.lang.Math.abs;
import static java.lang.Math.atan;
import static java.lang.Math.atan2;
import static java.lang.Math.sqrt;
import static java.lang.Math.toDegrees;
import static java.util.Collections.rotate;
import static org.opencv.imgproc.Imgproc.getRotationMatrix2D;
import static org.opencv.imgproc.Imgproc.warpAffine;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.RotatedRect;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

@EActivity(R.layout.activity_main)
public class MainActivity extends AppCompatActivity {
    private static final int RESULT_LOAD_IMG = 1;
    private static final int REQUEST_CODE_PERMISSION = 2;
    private static final String TAG = "MainActivity";
    private Mat mImg;

    // Storage Permissions
    private static String[] PERMISSIONS_REQ = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA
    };

    protected String mTestImgPath;
    // UI
    @ViewById(R.id.material_listview)
    protected MaterialListView mListView;
    @ViewById(R.id.fab)
    protected FloatingActionButton mFabActionBt;
    @ViewById(R.id.fab_cam)
    protected FloatingActionButton mFabCamActionBt;
    @ViewById(R.id.toolbar)
    protected Toolbar mToolbar;

    FaceDet mFaceDet;
    PedestrianDet mPersonDet;
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i("OpenCVManager setup", "OpenCV loaded successfully");
                    //Use openCV libraries after this
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };
    @Override
    public void onResume()
    {
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_9, this,
                mLoaderCallback);
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mListView = (MaterialListView) findViewById(R.id.material_listview);
        setSupportActionBar(mToolbar);
        // Just use hugo to print log
        isExternalStorageWritable();
        isExternalStorageReadable();

        // For API 23+ you need to request the read/write permissions even if they are already in your manifest.
        int currentapiVersion = android.os.Build.VERSION.SDK_INT;

        if (currentapiVersion >= Build.VERSION_CODES.M) {
            verifyPermissions(this);
        }
    }

    @AfterViews
    protected void setupUI() {
        mToolbar.setTitle(getString(R.string.app_name));
        Toast.makeText(MainActivity.this, getString(R.string.description_info), Toast.LENGTH_LONG).show();
    }

    @Click({R.id.fab})
    protected void launchGallery() {
        Toast.makeText(MainActivity.this, "Pick one image", Toast.LENGTH_SHORT).show();
        Intent galleryIntent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(galleryIntent, RESULT_LOAD_IMG);
    }

    @Click({R.id.fab_cam})
    protected void launchCameraPreview() {
        startActivity(new Intent(this, CameraActivity.class));
    }

    /**
     * Checks if the app has permission to write to device storage or open camera
     * If the app does not has permission then the user will be prompted to grant permissions
     *
     * @param activity
     */
    @DebugLog
    private static boolean verifyPermissions(Activity activity) {
        // Check if we have write permission
        int write_permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int read_persmission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int camera_permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.CAMERA);

        if (write_permission != PackageManager.PERMISSION_GRANTED ||
                read_persmission != PackageManager.PERMISSION_GRANTED ||
                camera_permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_REQ,
                    REQUEST_CODE_PERMISSION
            );
            return false;
        } else {
            return true;
        }
    }

    /* Checks if external storage is available for read and write */
    @DebugLog
    private boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    /* Checks if external storage is available to at least read */
    @DebugLog
    private boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return true;
        }
        return false;
    }

    @DebugLog
    protected void demoStaticImage() {
        if (mTestImgPath != null) {
            Timber.tag(TAG).d("demoStaticImage() launch a task to det");
            runDetectAsync(mTestImgPath);
        } else {
            Timber.tag(TAG).d("demoStaticImage() mTestImgPath is null, go to gallery");
            Toast.makeText(MainActivity.this, "Pick an image to run algorithms", Toast.LENGTH_SHORT).show();
            // Create intent to Open Image applications like Gallery, Google Photos
            Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(galleryIntent, RESULT_LOAD_IMG);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_CODE_PERMISSION) {
            Toast.makeText(MainActivity.this, "Demo using static images", Toast.LENGTH_SHORT).show();
            demoStaticImage();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        try {
            // When an Image is picked
            if (requestCode == RESULT_LOAD_IMG && resultCode == RESULT_OK && null != data) {
                // Get the Image from data
                Uri selectedImage = data.getData();
                String[] filePathColumn = {MediaStore.Images.Media.DATA};
                // Get the cursor
                Cursor cursor = getContentResolver().query(selectedImage, filePathColumn, null, null, null);
                cursor.moveToFirst();
                int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                mTestImgPath = cursor.getString(columnIndex);
                cursor.close();

                if (mTestImgPath != null) {
                    runDetectAsync(mTestImgPath);
                    Toast.makeText(this, "Img Path:" + mTestImgPath, Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "You haven't picked Image", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Something went wrong", Toast.LENGTH_LONG).show();
        }
    }

    // ==========================================================
    // Tasks inner class
    // ==========================================================
    private ProgressDialog mDialog;

    @Background
    @NonNull
    protected void runDetectAsync(@NonNull String imgPath) {
        showDiaglog();

        final String targetPath = Constants.getFaceShapeModelPath();
        if (!new File(targetPath).exists()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "Copy landmark model to " + targetPath, Toast.LENGTH_SHORT).show();
                }
            });
            FileUtils.copyFileFromRawToOthers(getApplicationContext(), R.raw.shape_predictor_68_face_landmarks, targetPath);
        }
        // Init
       /* if (mPersonDet == null) {
            mPersonDet = new PedestrianDet();
        }*/
        if (mFaceDet == null) {
            mFaceDet = new FaceDet(Constants.getFaceShapeModelPath());
        }

        Timber.tag(TAG).d("Image path: " + imgPath);
        List<Card> cardrets = new ArrayList<>();
        List<VisionDetRet> faceList = mFaceDet.detect(imgPath);


        // get the largest face ( Main face )
        // erase others

        Iterator itFaceList = faceList.iterator();

/*
       if(faceList.size()>0){
        int nFaceSize=0;
        int nTemp=0;
        for(int i=0;i<faceList.size();i++){
            nFaceSize=abs(faceList.get(i).getTop()-faceList.get(i).getBottom()) * abs(faceList.get(i).getLeft()-faceList.get(i).getRight());

            if()
        }

       }
*/



        if (faceList.size() > 0) {
            Card card = new Card.Builder(MainActivity.this)
                    .withProvider(BigImageCardProvider.class)
                    .setDrawable(drawRect(imgPath, faceList, Color.GREEN))
                    .setTitle("Original Landmark")
                    .endConfig()
                    .build();
            cardrets.add(card);


            Card LandCard = new Card.Builder(MainActivity.this)
                    .withProvider(BigImageCardProvider.class)
                    .setDrawable(drawLandmark(imgPath, faceList, Color.GREEN))
                    .setTitle("White Landmark")
                    .endConfig()
                    .build();
            cardrets.add(LandCard);

        } else {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), "No face", Toast.LENGTH_SHORT).show();
                }
            });
        }

     /*   List<VisionDetRet> personList = mPersonDet.detect(imgPath);
        if (personList.size() > 0) {
            Card card = new Card.Builder(MainActivity.this)
                    .withProvider(BigImageCardProvider.class)
                    .setDrawable(drawRect(imgPath, personList, Color.BLUE))
                    .setTitle("Person det")
                    .endConfig()
                    .build();
            cardrets.add(card);
        } else {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), "No person", Toast.LENGTH_SHORT).show();
                }
            });
        }
*/
        addCardListView(cardrets);
        dismissDialog();
    }

    @UiThread
    protected void addCardListView(List<Card> cardrets) {
        for (Card each : cardrets) {
            mListView.add(each);
        }
    }

    @UiThread
    protected void showDiaglog() {
        mDialog = ProgressDialog.show(MainActivity.this, "Wait", "Person and face detection", true);
    }

    @UiThread
    protected void dismissDialog() {
        if (mDialog != null) {
            mDialog.dismiss();
        }
    }

    public int exifOrientationToDegrees(int exifOrientation){
    if(exifOrientation == ExifInterface.ORIENTATION_ROTATE_90) {
        return 90;
         } else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_180) {
           return 180;
          } else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_270) {
           return 270;
         } return 0;
    }




    @DebugLog
    protected BitmapDrawable drawRect(String path, List<VisionDetRet> results, int color)  {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        Bitmap bm = BitmapFactory.decodeFile(path, options);
        android.graphics.Bitmap.Config bitmapConfig = bm.getConfig();
        // set default bitmap config if none
        if (bitmapConfig == null) {
            bitmapConfig = android.graphics.Bitmap.Config.ARGB_8888;
        }
        ExifInterface exif = null;
        try {
            exif = new ExifInterface(path);
        } catch (IOException e) {
            e.printStackTrace();
        }


        int exifOrientation = exif.getAttributeInt(
               ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
     int exifDegree = exifOrientationToDegrees(exifOrientation);
        Matrix rotateMatrix = new Matrix();
        rotateMatrix.postRotate(exifDegree);

        bm = Bitmap.createBitmap(bm, 0, 0,
                bm.getWidth(),bm.getHeight(), rotateMatrix, false);

        // resource bitmaps are imutable,
        // so we need to convert it to mutable one
        bm = bm.copy(bitmapConfig, true);
        int width = bm.getWidth();
        int height = bm.getHeight();
        // By ratio scale
        float aspectRatio = bm.getWidth() / (float) bm.getHeight();
        final int MAX_SIZE = 512;
        int newWidth = MAX_SIZE;
        int newHeight = MAX_SIZE;
        float resizeRatio = 1;
        newHeight = Math.round(newWidth / aspectRatio);
        if (bm.getWidth() > MAX_SIZE && bm.getHeight() > MAX_SIZE) {
            Timber.tag(TAG).d("Resize Bitmap");
            bm = getResizedBitmap(bm, newWidth, newHeight);
            resizeRatio = (float) bm.getWidth() / (float) width;
            Timber.tag(TAG).d("resizeRatio " + resizeRatio);
        }


        // Create canvas to draw
        Canvas canvas = new Canvas(bm);
        Paint paint = new Paint();
        paint.setColor(color);
        paint.setStrokeWidth(2);
        paint.setStyle(Paint.Style.STROKE);
        // Loop result list
        Paint JawPaint = new Paint();
        JawPaint.setColor(Color.RED);
        JawPaint.setStrokeWidth(2);
        JawPaint.setStyle(Paint.Style.STROKE);
        //jaw
        int Number=0;

/*        for(VisionDetRet ret: results){
            ret.
        }
*/

        for (VisionDetRet ret : results) {

            Rect bounds = new Rect();
            bounds.left = (int) (ret.getLeft() * resizeRatio);
            bounds.top = (int) (ret.getTop() * resizeRatio);
            bounds.right = (int) (ret.getRight() * resizeRatio);
            bounds.bottom = (int) (ret.getBottom() * resizeRatio);
            canvas.drawRect(bounds, paint);
            // Get landmark
            ArrayList<Point> landmarks = ret.getFaceLandmarks();

            for (Point point : landmarks) {
                int pointX = (int) (point.x * resizeRatio);
                int pointY = (int) (point.y * resizeRatio);

                if(Number<17) canvas.drawCircle(pointX, pointY, 2, JawPaint);
                else if (Number<22)  {
                    JawPaint.setColor(Color.BLUE);
                    canvas.drawCircle(pointX, pointY, 2, JawPaint);
                }
                else if (Number<27)  {
                    JawPaint.setColor(Color.CYAN);
                    canvas.drawCircle(pointX, pointY, 2, JawPaint);
                }
                else if (Number<36)  {
                    JawPaint.setColor(Color.BLACK);
                    canvas.drawCircle(pointX, pointY, 2, JawPaint);
                }
                else if (Number<42)  {
                    JawPaint.setColor(Color.GRAY);
                    canvas.drawCircle(pointX, pointY, 2, JawPaint);
                }
                else if (Number<48)  {
                    JawPaint.setColor(Color.GRAY);
                    canvas.drawCircle(pointX, pointY, 2, JawPaint);
                }
                else if (Number<68)  {
                    JawPaint.setColor(Color.YELLOW);
                    canvas.drawCircle(pointX, pointY, 2, JawPaint);
                }
                Number++;
            }
        }

        return new BitmapDrawable(getResources(), bm);
    }

    @DebugLog
    protected BitmapDrawable drawLandmark(String path, List<VisionDetRet> results, int color) {

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;

       // Bitmap OriginBm = BitmapFactory.decodeFile(path, options);
        //        Bitmap bm = Bitmap.createBitmap(OriginBm.getWidth(),OriginBm.getHeight(), Bitmap.Config.ARGB_8888);
        Bitmap bm =  BitmapFactory.decodeFile(path, options);
      // original은 원본이미지
        // bm은 landmark만 출력하기 위한 ...


        android.graphics.Bitmap.Config bitmapConfig = bm.getConfig();
        // set default bitmap config if none
        if (bitmapConfig == null) {
            bitmapConfig = android.graphics.Bitmap.Config.ARGB_8888;
        }
        // resource bitmaps are imutable,
        // so we need to convert it to mutable one

        if (bitmapConfig == null) {
            bitmapConfig = android.graphics.Bitmap.Config.ARGB_8888;
        }
        ExifInterface exif = null;
        try {
            exif = new ExifInterface(path);
        } catch (IOException e) {
            e.printStackTrace();
        }
        int exifOrientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        int exifDegree = exifOrientationToDegrees(exifOrientation);
        Matrix rotateMatrix = new Matrix();
        rotateMatrix.postRotate(exifDegree);

        bm = Bitmap.createBitmap(bm, 0, 0,
                bm.getWidth(),bm.getHeight(), rotateMatrix, false);

        bm = bm.copy(bitmapConfig, true);
        int width = bm.getWidth();
        int height = bm.getHeight();
        // By ratio scale
        float aspectRatio = bm.getWidth() / (float) bm.getHeight();
        final int MAX_SIZE = 512;
        int newWidth = MAX_SIZE;
        int newHeight = MAX_SIZE;
        float resizeRatio = 1;



        newHeight = Math.round(newWidth / aspectRatio);
        if (bm.getWidth() > MAX_SIZE && bm.getHeight() > MAX_SIZE) {
            Timber.tag(TAG).d("Resize Bitmap");
            bm = getResizedBitmap(bm, newWidth, newHeight);
            resizeRatio = (float) bm.getWidth() / (float) width;
            Timber.tag(TAG).d("resizeRatio " + resizeRatio);
        }



        // Create canvas to draw
        Canvas canvas = new Canvas(bm);
        Paint paint = new Paint();
        paint.setColor(color);
        paint.setStrokeWidth(2);
        paint.setStyle(Paint.Style.STROKE);
        // Loop result list
        Paint JawPaint = new Paint();
        JawPaint.setColor(Color.RED);
        JawPaint.setStrokeWidth(2);
        JawPaint.setStyle(Paint.Style.STROKE);
        //jaw
        int Number=0;

/*        for(VisionDetRet ret: results){
            ret.
        }
*/
        Mat dst = new Mat();
        for (VisionDetRet ret : results) {

            Rect bounds = new Rect();
            bounds.left = (int) (ret.getLeft() * resizeRatio);
            bounds.top = (int) (ret.getTop() * resizeRatio);
            bounds.right = (int) (ret.getRight() * resizeRatio);
            bounds.bottom = (int) (ret.getBottom() * resizeRatio);
//           canvas.drawRect(bounds, paint);
            ArrayList<Point> landmarks = ret.getFaceLandmarks();


            int nCenterReyeX=((int) (landmarks.get(36).x * resizeRatio)+(int) (landmarks.get(39).x * resizeRatio))/2;
            int nCenterReyeY=((int) (landmarks.get(36).y * resizeRatio)+(int) (landmarks.get(39).y * resizeRatio))/2;

            int nCenterLeyeX=((int) (landmarks.get(42).x * resizeRatio)+(int) (landmarks.get(45).x * resizeRatio))/2;
            int nCenterLeyeY=((int) (landmarks.get(42).y * resizeRatio)+(int) (landmarks.get(45).y * resizeRatio))/2;

          //  canvas.drawLine((int) (landmarks.get(36).x * resizeRatio),(int) (landmarks.get(36).y* resizeRatio),(int) (landmarks.get(39).x * resizeRatio),(int) (landmarks.get(39).y* resizeRatio),JawPaint);
        //    canvas.drawLine((int) (landmarks.get(42).x * resizeRatio),(int) (landmarks.get(42).y* resizeRatio),(int) (landmarks.get(45).x * resizeRatio),(int) (landmarks.get(45).y* resizeRatio),JawPaint);
            // 양쪽 눈 라인


           // canvas.drawCircle(nCenterLeyeX,nCenterLeyeY,2,JawPaint);
           // canvas.drawCircle(nCenterReyeX,nCenterReyeY,2,JawPaint);
            // 양쪽 눈의 중심점
            Paint pCenter = new Paint();
            pCenter.setColor(Color.GREEN);
            pCenter.setStrokeWidth(2);
            pCenter.setStyle(Paint.Style.STROKE);

            for (Point point : landmarks) {
                int pointX = (int) (point.x * resizeRatio);
                int pointY = (int) (point.y * resizeRatio);
/*
                if(Number<17) canvas.drawCircle(pointX, pointY, 2, JawPaint);
                else if (Number<22)  {
                    JawPaint.setColor(Color.BLUE);
                    canvas.drawCircle(pointX, pointY, 2, JawPaint);
                }
                else if (Number<27)  {
                    JawPaint.setColor(Color.CYAN);
                    canvas.drawCircle(pointX, pointY, 2, JawPaint);
                }
                else if (Number<36)  {
                    JawPaint.setColor(Color.BLACK);
                    canvas.drawCircle(pointX, pointY, 2, JawPaint);
                }
                else if (Number<42)  {
                    JawPaint.setColor(Color.GRAY);
                    canvas.drawCircle(pointX, pointY, 2, JawPaint);
                }
                else if (Number<48)  {
                    JawPaint.setColor(Color.GRAY);
                    canvas.drawCircle(pointX, pointY, 2, JawPaint);
                }
                else if (Number<68)  {
                    JawPaint.setColor(Color.YELLOW);
                    canvas.drawCircle(pointX, pointY, 2, JawPaint);
                }
                Number++;

                canvas.drawLine(nCenterLeyeX,nCenterLeyeY,nCenterReyeX,nCenterReyeY,pCenter);
                canvas.drawCircle((nCenterReyeX+nCenterLeyeX)/2,(nCenterReyeY+nCenterLeyeY)/2,2,JawPaint);
                // 눈의 중심선분의 중심점
*/
              //  double dY = abs(nCenterLeyeY-nCenterReyeY);
             //   double dX = abs(nCenterLeyeX-nCenterReyeX);
                double dY = nCenterReyeY-nCenterLeyeY;
                double dX = nCenterReyeX-nCenterLeyeX;

                org.opencv.core.Point desiredRightEye= new org.opencv.core.Point();
                org.opencv.core.Point desiredLeftEye = new org.opencv.core.Point();

                int desiredFaceWidth=256;
                int desiredFaceHeight;
                desiredLeftEye.x=0.35;
                desiredLeftEye.y=0.35;
                desiredRightEye.x=0.65;
                desiredRightEye.y=0.65;
                double dist = sqrt((dX*dX)+(dY*dY));
                double desiredDist=  (desiredRightEye.x-desiredLeftEye.x);
                desiredDist=desiredDist*desiredFaceWidth;
                double scale = desiredDist/dist;



                double angle= toDegrees(atan2(dY,dX))-180;
                Mat mat = new Mat();
                Bitmap bmp32 = bm.copy(Bitmap.Config.ARGB_8888, true);
                Utils.bitmapToMat(bmp32, mat);

                org.opencv.core.Point ptCenter=new org.opencv.core.Point();
                ptCenter.x=(nCenterLeyeX+nCenterReyeX)/2.0;
                ptCenter.y=(nCenterReyeY+nCenterReyeY)/2.0;
                Mat r = getRotationMatrix2D(ptCenter,angle,1.0);
                org.opencv.core.Rect bbox= new RotatedRect(ptCenter,mat.size(),angle).boundingRect();
                warpAffine(mat,dst,r,bbox.size());



    /*
               Utils.matToBitmap(dst,bm);
    */
           //     canvas.drawRect(bounds, paint);


            }
        }

        Bitmap bmp ;

        bmp = Bitmap.createBitmap(dst.cols(),dst.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(dst,bmp);

        List<VisionDetRet> NewFaceList = mFaceDet.detect(bmp);
        Iterator itFaceList = NewFaceList.iterator();

        Canvas NewCanvas=new Canvas(bmp);
        Rect bounds = new Rect();
        for(VisionDetRet ret:NewFaceList){
            bounds.left = (int) (ret.getLeft());
            bounds.top = (int) (ret.getTop());
            bounds.right = (int) (ret.getRight() );
            bounds.bottom = (int) (ret.getBottom() );

            CalculateDateSet(ret);
        //    NewCanvas.drawRect(bounds, paint);
        }

        Bitmap CropBmp = Bitmap.createBitmap(bmp,bounds.left,bounds.top,bounds.width(),bounds.height());



        return new BitmapDrawable(getResources(), CropBmp);
    }

    protected class FaceDataSetClass{

        double dGrlabella;
        double dNoseHeight;
        double dNoseWidth;
        double dEyeHeight;
        double dEyeWidth;
        double dMouthwidth;
        double dFaceHeight;
        double dFaceWidth;
        double dSkinColor;

        private FaceDataSetClass() {
        }

        public double getdGrlabella() {
            return dGrlabella;
        }
        public void setdGrlabella(double dGrlabella) {
            this.dGrlabella = dGrlabella;
        }

        public double getdFaceHeight() {
            return dFaceHeight;
        }

        public void setdFaceHeight(double dFaceHeight) {
            this.dFaceHeight = dFaceHeight;
        }

        public double getdFaceWidth() {
            return dFaceWidth;
        }

        public void setdFaceWidth(double dFaceWidth) {
            this.dFaceWidth = dFaceWidth;
        }

        public double getdMouthwidth() {
            return dMouthwidth;
        }

        public void setdMouthwidth(double dMouthwidth) {
            this.dMouthwidth = dMouthwidth;
        }

        public double getdNoseHeight() {
            return dNoseHeight;
        }

        public void setdNoseHeight(double dNoseHeight) {
            this.dNoseHeight = dNoseHeight;
        }

        public double getdNoseWidth() {
            return dNoseWidth;
        }

        public void setdNoseWidth(double dNoseWidth) {
            this.dNoseWidth = dNoseWidth;
        }

        public double getdSkinColor() {
            return dSkinColor;
        }

        public void setdSkinColor(double dSkinColor) {
            this.dSkinColor = dSkinColor;
        }

        public double getdEyeWidth() {
            return dEyeWidth;
        }

        public void setdEyeWidth(double dEyeWidth) {
            this.dEyeWidth = dEyeWidth;
        }

        public double getdEyeHeight() {
            return dEyeHeight;
        }

        public void setdEyeHeight(double dEyeHeight) {
            this.dEyeHeight = dEyeHeight;
        }
    }


   protected void CalculateDateSet(VisionDetRet ret){

        FaceDataSetClass  FaceSet = new FaceDataSetClass();

//      Vector<Integer> nDataSet_vector;
        ArrayList<Point> landmarks = ret.getFaceLandmarks();

        FaceSet.setdEyeHeight(abs(landmarks.get(38).y-landmarks.get(40).y));
        FaceSet.setdEyeWidth(abs(landmarks.get(39).x-landmarks.get(36).x));
        FaceSet.setdMouthwidth(abs(landmarks.get(54).x-landmarks.get(48).x));
        FaceSet.setdNoseHeight(abs(landmarks.get(27).y-landmarks.get(33).y));
        FaceSet.setdGrlabella(abs(landmarks.get(39).x-landmarks.get(42).x));
        FaceSet.setdFaceHeight(abs(landmarks.get(8).y-((landmarks.get(21).y+landmarks.get(22).y)/2)));
        FaceSet.setdFaceWidth((landmarks.get(16).x-landmarks.get(0).x));
        FaceSet.setdNoseWidth((landmarks.get(31).x-landmarks.get(35).x));

   }

    @DebugLog
    protected Bitmap getResizedBitmap(Bitmap bm, int newWidth, int newHeight) {
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bm, newWidth, newHeight, true);
        return resizedBitmap;
    }
}
