/*
 *  UVCCamera
 *  library and sample to access to UVC web camera on non-rooted Android device
 *
 * Copyright (c) 2014-2017 saki t_saki@serenegiant.com
 *
 * Modified 2021 by Raphael Michel to allow "cropping"
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 *  All files in the folder are under this Apache License, Version 2.0.
 *  Files in the libjpeg-turbo, libusb, libuvc, rapidjson folder
 *  may have a different license, see the respective files.
 */

package eu.pretix.libpretixui.android.uvc;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.opengl.Matrix;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import com.serenegiant.encoder.IVideoEncoder;
import com.serenegiant.encoder.MediaEncoder;
import com.serenegiant.encoder.MediaVideoEncoder;
import com.serenegiant.glutils.EGLBase;
import com.serenegiant.glutils.GLDrawer2D;
import com.serenegiant.glutils.es1.GLHelper;
import com.serenegiant.utils.FpsCounter;
import com.serenegiant.widget.AspectRatioTextureView;
import com.serenegiant.widget.CameraViewInterface;

import java.lang.reflect.Array;
import java.util.Arrays;

import kotlin.NotImplementedError;

/**
 * change the view size with keeping the specified aspect ratio.
 * if you set this view with in a FrameLayout and set property "android:layout_gravity="center",
 * you can show this view in the center of screen and keep the aspect ratio of content
 * XXX it is better that can set the aspect ratio as xml property
 */
public class UVCCameraTextureView extends AspectRatioTextureView    // API >= 14
        implements TextureView.SurfaceTextureListener, CameraViewInterface {

    private static final boolean DEBUG = true;    // TODO set false on release
    private static final String TAG = "UVCCameraTextureView";

    private boolean mHasSurface;
    private RenderHandler mRenderHandler;
    private final Object mCaptureSync = new Object();
    private Bitmap mTempBitmap;
    private boolean mReqesutCaptureStillImage;
    private Callback mCallback;
    private int mDataWidth = 640;
    private int mDataHeight = 480;
    /**
     * for calculation of frame rate
     */
    private final FpsCounter mFpsCounter = new FpsCounter();

    public UVCCameraTextureView(final Context context) {
        this(context, null, 0);
    }

    public UVCCameraTextureView(final Context context, final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public UVCCameraTextureView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
        setSurfaceTextureListener(this);
    }

    public void setDataSize(int w, int h) {
        mDataWidth = w;
        mDataHeight = h;
        Log.d(TAG, "setDataSize(" + w + ", "+ h + ")");
        if (mRenderHandler != null) {
            mRenderHandler.dataSizeChanged(w, h);
        }
    }

    @Override
    public void onResume() {
        if (DEBUG) Log.v(TAG, "onResume:");
        if (mHasSurface) {
            mRenderHandler = RenderHandler.createHandler(mFpsCounter, super.getSurfaceTexture(), getWidth(), getHeight(), mDataWidth, mDataHeight);
        }
    }

    @Override
    public void onPause() {
        if (DEBUG) Log.v(TAG, "onPause:");
        if (mRenderHandler != null) {
            mRenderHandler.release();
            mRenderHandler = null;
        }
        if (mTempBitmap != null) {
            mTempBitmap.recycle();
            mTempBitmap = null;
        }
    }

    @Override
    public void onSurfaceTextureAvailable(final SurfaceTexture surface, final int width, final int height) {
        if (DEBUG) Log.v(TAG, "onSurfaceTextureAvailable:" + surface);
        if (mRenderHandler == null) {
            mRenderHandler = RenderHandler.createHandler(mFpsCounter, surface, width, height, mDataWidth, mDataHeight);
        } else {
            mRenderHandler.resize(width, height);
        }
        mHasSurface = true;
        if (mCallback != null) {
            mCallback.onSurfaceCreated(this, getSurface());
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(final SurfaceTexture surface, final int width, final int height) {
        if (DEBUG) Log.v(TAG, "onSurfaceTextureSizeChanged:" + surface);
        if (mRenderHandler != null) {
            mRenderHandler.resize(width, height);
        }
        if (mCallback != null) {
            mCallback.onSurfaceChanged(this, getSurface(), width, height);
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(final SurfaceTexture surface) {
        if (DEBUG) Log.v(TAG, "onSurfaceTextureDestroyed:" + surface);
        if (mRenderHandler != null) {
            mRenderHandler.release();
            mRenderHandler = null;
        }
        mHasSurface = false;
        if (mCallback != null) {
            mCallback.onSurfaceDestroy(this, getSurface());
        }
        if (mPreviewSurface != null) {
            mPreviewSurface.release();
            mPreviewSurface = null;
        }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(final SurfaceTexture surface) {
        synchronized (mCaptureSync) {
            if (mReqesutCaptureStillImage) {
                mReqesutCaptureStillImage = false;
                if (mTempBitmap == null)
                    mTempBitmap = getBitmap();
                else
                    getBitmap(mTempBitmap);
                mCaptureSync.notifyAll();
            }
        }
    }

    @Override
    public boolean hasSurface() {
        return mHasSurface;
    }

    /**
     * capture preview image as a bitmap
     * this method blocks current thread until bitmap is ready
     * if you call this method at almost same time from different thread,
     * the returned bitmap will be changed while you are processing the bitmap
     * (because we return same instance of bitmap on each call for memory saving)
     * if you need to call this method from multiple thread,
     * you should change this method(copy and return)
     */
    @Override
    public Bitmap captureStillImage() {
        synchronized (mCaptureSync) {
            mReqesutCaptureStillImage = true;
            try {
                mCaptureSync.wait();
            } catch (final InterruptedException e) {
            }
            return mTempBitmap;
        }
    }

    @Override
    public SurfaceTexture getSurfaceTexture() {
        return mRenderHandler != null ? mRenderHandler.getPreviewTexture() : null;
    }

    private Surface mPreviewSurface;

    @Override
    public Surface getSurface() {
        if (DEBUG) Log.v(TAG, "getSurface:hasSurface=" + mHasSurface);
        if (mPreviewSurface == null) {
            final SurfaceTexture st = getSurfaceTexture();
            if (st != null) {
                mPreviewSurface = new Surface(st);
            }
        }
        return mPreviewSurface;
    }

    @Override
    public void setVideoEncoder(final IVideoEncoder encoder) {
        throw new NotImplementedError();
    }

    @Override
    public void setCallback(final Callback callback) {
        mCallback = callback;
    }

    public void resetFps() {
        mFpsCounter.reset();
    }

    /**
     * update frame rate of image processing
     */
    public void updateFps() {
        mFpsCounter.update();
    }

    /**
     * get current frame rate of image processing
     *
     * @return
     */
    public float getFps() {
        return mFpsCounter.getFps();
    }

    /**
     * get total frame rate from start
     *
     * @return
     */
    public float getTotalFps() {
        return mFpsCounter.getTotalFps();
    }

    /**
     * render camera frames on this view on a private thread
     *
     * @author saki
     */
    private static final class RenderHandler extends Handler
            implements SurfaceTexture.OnFrameAvailableListener {

        private static final int MSG_REQUEST_RENDER = 1;
        private static final int MSG_CREATE_SURFACE = 3;
        private static final int MSG_RESIZE = 4;
        private static final int MSG_TERMINATE = 9;
        private static final int MSG_CHANGE_DATA_SIZE = 12;

        private RenderThread mThread;
        private boolean mIsActive = true;
        private final FpsCounter mFpsCounter;

        public static final RenderHandler createHandler(final FpsCounter counter,
                                                        final SurfaceTexture surface, final int width, final int height, final int dataWidth, final int dataHeight) {

            final RenderThread thread = new RenderThread(counter, surface, width, height, dataWidth, dataHeight);
            thread.start();
            return thread.getHandler();
        }

        private RenderHandler(final FpsCounter counter, final RenderThread thread) {
            mThread = thread;
            mFpsCounter = counter;
        }

        public final SurfaceTexture getPreviewTexture() {
            if (DEBUG) Log.v(TAG, "getPreviewTexture:");
            if (mIsActive) {
                synchronized (mThread.mSync) {
                    sendEmptyMessage(MSG_CREATE_SURFACE);
                    try {
                        mThread.mSync.wait();
                    } catch (final InterruptedException e) {
                    }
                    return mThread.mPreviewSurface;
                }
            } else {
                return null;
            }
        }

        public void dataSizeChanged(final int width, final int height) {
            Log.d(TAG, "dataSizeChanged(" + width + ", "+ height + ")");
            if (mIsActive) {
                synchronized (mThread.mSync) {
                    sendMessage(obtainMessage(MSG_CHANGE_DATA_SIZE, width, height));
                    try {
                        mThread.mSync.wait();
                    } catch (final InterruptedException e) {
                    }
                }
            }
        }

        public void resize(final int width, final int height) {
            if (DEBUG) Log.v(TAG, "resize:");
            if (mIsActive) {
                synchronized (mThread.mSync) {
                    sendMessage(obtainMessage(MSG_RESIZE, width, height));
                    try {
                        mThread.mSync.wait();
                    } catch (final InterruptedException e) {
                    }
                }
            }
        }

        public final void release() {
            if (DEBUG) Log.v(TAG, "release:");
            if (mIsActive) {
                mIsActive = false;
                removeMessages(MSG_REQUEST_RENDER);
                sendEmptyMessage(MSG_TERMINATE);
            }
        }

        @Override
        public final void onFrameAvailable(final SurfaceTexture surfaceTexture) {
            if (mIsActive) {
                mFpsCounter.count();
                sendEmptyMessage(MSG_REQUEST_RENDER);
            }
        }

        @Override
        public final void handleMessage(final Message msg) {
            if (mThread == null) return;
            switch (msg.what) {
                case MSG_REQUEST_RENDER:
                    mThread.onDrawFrame();
                    break;
                case MSG_CREATE_SURFACE:
                    mThread.updatePreviewSurface();
                    break;
                case MSG_CHANGE_DATA_SIZE:
                    mThread.setDataSize(msg.arg1, msg.arg2);
                    break;
                case MSG_RESIZE:
                    mThread.resize(msg.arg1, msg.arg2);
                    break;
                case MSG_TERMINATE:
                    Looper.myLooper().quit();
                    mThread = null;
                    break;
                default:
                    super.handleMessage(msg);
            }
        }

        private static final class RenderThread extends Thread {
            private final Object mSync = new Object();
            private final SurfaceTexture mSurface;
            private RenderHandler mHandler;
            private EGLBase mEgl;
            /**
             * IEglSurface instance related to this TextureView
             */
            private EGLBase.IEglSurface mEglSurface;
            private GLDrawer2D mDrawer;
            private int mTexId = -1;
            /**
             * SurfaceTexture instance to receive video images
             */
            private SurfaceTexture mPreviewSurface;
            private final float[] mStMatrix = new float[16];
            private final float[] mTmpMatrix = new float[16];
            private final float[] mOurMatrix = new float[16];
            private int mViewWidth, mViewHeight, mDataWidth, mDataHeight;
            private final FpsCounter mFpsCounter;

            /**
             * constructor
             *
             * @param surface: drawing surface came from TexureView
             */
            public RenderThread(final FpsCounter fpsCounter, final SurfaceTexture surface, final int width, final int height, final int dataWidth, final int dataHeight) {
                mFpsCounter = fpsCounter;
                mSurface = surface;
                mViewWidth = width;
                mViewHeight = height;
                mDataWidth = dataWidth;
                mDataHeight = dataHeight;
                updateOurMatrix();
                setName("RenderThread");
            }

            public final RenderHandler getHandler() {
                if (DEBUG) Log.v(TAG, "RenderThread#getHandler:");
                synchronized (mSync) {
                    // create rendering thread
                    if (mHandler == null)
                        try {
                            mSync.wait();
                        } catch (final InterruptedException e) {
                        }
                }
                return mHandler;
            }

            public void setDataSize(final int w, final int h) {
                Log.d(TAG, "RenderThread.setDataSize(" + w + ", "+ h + ")");
                mDataWidth = w;
                mDataHeight = h;
                updateOurMatrix();
                synchronized (mSync) {
                    mSync.notifyAll();
                }
            }

            public void resize(final int width, final int height) {
                if (((width > 0) && (width != mViewWidth)) || ((height > 0) && (height != mViewHeight))) {
                    mViewWidth = width;
                    mViewHeight = height;
                    updateOurMatrix();
                    updatePreviewSurface();
                } else {
                    synchronized (mSync) {
                        mSync.notifyAll();
                    }
                }
            }

            public final void updateOurMatrix() {
                // this calculation is probably only correct if the aspect ratio of the view is
                // smaller than of the source, which in our case should always be true (landscape
                // cameras, portrait viewport).
                float aspectRatioData = mDataWidth / (float) mDataHeight;
                float aspectRatioView = mViewWidth / (float) mViewHeight;

                float visibleDataWith = mDataHeight * aspectRatioView;

                Matrix.setIdentityM(mOurMatrix, 0);
                Matrix.scaleM(mOurMatrix, 0, 1 / (aspectRatioData / aspectRatioView), 1, 1);
                Matrix.translateM(mOurMatrix, 0, (mDataWidth - visibleDataWith) / (1f * mDataWidth), 0, 0);
                Log.i("RenderThread", "ourMatrix=" + Arrays.toString(mOurMatrix));
            }

            public final void updatePreviewSurface() {
                if (DEBUG) Log.i(TAG, "RenderThread#updatePreviewSurface:");
                synchronized (mSync) {
                    if (mPreviewSurface != null) {
                        if (DEBUG) Log.d(TAG, "updatePreviewSurface:release mPreviewSurface");
                        mPreviewSurface.setOnFrameAvailableListener(null);
                        mPreviewSurface.release();
                        mPreviewSurface = null;
                    }
                    mEglSurface.makeCurrent();
                    if (mTexId >= 0) {
                        mDrawer.deleteTex(mTexId);
                    }
                    // create texture and SurfaceTexture for input from camera
                    mTexId = mDrawer.initTex();
                    if (DEBUG) Log.v(TAG, "updatePreviewSurface:tex_id=" + mTexId);
                    mPreviewSurface = new SurfaceTexture(mTexId);
                    mPreviewSurface.setDefaultBufferSize(mViewWidth, mViewHeight);
                    mPreviewSurface.setOnFrameAvailableListener(mHandler);
                    // notify to caller thread that previewSurface is ready
                    mSync.notifyAll();
                }
            }

            public final void onDrawFrame() {
                mEglSurface.makeCurrent();
                // update texture(came from camera)
                mPreviewSurface.updateTexImage();
                // get texture matrix
                mPreviewSurface.getTransformMatrix(mTmpMatrix);

                Matrix.multiplyMM(mStMatrix, 0, mOurMatrix, 0, mTmpMatrix, 0);

                mDrawer.draw(mTexId, mStMatrix, 0);
                mEglSurface.swap();
            }

            @Override
            public final void run() {
                Log.d(TAG, getName() + " started");
                init();
                Looper.prepare();
                synchronized (mSync) {
                    mHandler = new RenderHandler(mFpsCounter, this);
                    mSync.notify();
                }

                Looper.loop();

                Log.d(TAG, getName() + " finishing");
                release();
                synchronized (mSync) {
                    mHandler = null;
                    mSync.notify();
                }
            }

            private final void init() {
                if (DEBUG) Log.v(TAG, "RenderThread#init:");
                // create EGLContext for this thread
                mEgl = EGLBase.createFrom(null, false, false);
                mEglSurface = mEgl.createFromSurface(mSurface);
                mEglSurface.makeCurrent();
                // create drawing object
                mDrawer = new GLDrawer2D(true);
            }

            private final void release() {
                if (DEBUG) Log.v(TAG, "RenderThread#release:");
                if (mDrawer != null) {
                    mDrawer.release();
                    mDrawer = null;
                }
                if (mPreviewSurface != null) {
                    mPreviewSurface.release();
                    mPreviewSurface = null;
                }
                if (mTexId >= 0) {
                    GLHelper.deleteTex(mTexId);
                    mTexId = -1;
                }
                if (mEglSurface != null) {
                    mEglSurface.release();
                    mEglSurface = null;
                }
                if (mEgl != null) {
                    mEgl.release();
                    mEgl = null;
                }
            }
        }
    }
}