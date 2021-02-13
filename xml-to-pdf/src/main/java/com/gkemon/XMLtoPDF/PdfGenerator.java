package com.gkemon.XMLtoPDF;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Environment;
import android.os.StrictMode;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.core.content.ContextCompat;

import com.gkemon.XMLtoPDF.model.FailureResponse;
import com.gkemon.XMLtoPDF.model.SuccessResponse;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class PdfGenerator {

    public static double postScriptThreshold = 0.75;
    public static int a4HeightInPX = 3508;
    public static int a4WidthInPX = 2480;
    public static int a5HeightInPX = 1748;
    public static int a5WidthInPX = 2480;

    public static int a4HeightInPostScript = (int) (a4HeightInPX * postScriptThreshold);
    public static int a4WidthInPostScript = (int) (a4WidthInPX * postScriptThreshold);

    public static int WRAP_CONTENT_WIDTH = 0, WRAP_CONTENT_HEIGHT = 0;

    public static ContextStep getBuilder() {
        return new Builder();
    }

    public enum PageSize {
        /**
         * For standard A4 size page
         */
        A4,

        /**
         * For standard A5 size page
         */
        A5,
        /**
         * For print the page as much as they are big.
         */
        WRAP_CONTENT
    }


    public interface ContextStep {
        FromSourceStep setContext(Context context);
    }

    public interface FromSourceStep {
        LayoutXMLSourceIntakeStep fromLayoutXMLSource();

        ViewIDSourceIntakeStep fromViewIDSource();

        ViewSourceIntakeStep fromViewSource();
    }


    public interface ViewSourceIntakeStep {
        PageSizeStep fromView(View... viewList);

        PageSizeStep fromViewList(List<View> viewList);
    }

    public interface LayoutXMLSourceIntakeStep {
        PageSizeStep fromLayoutXML(@LayoutRes Integer... layoutXMLs);

        PageSizeStep fromLayoutXMLList(@LayoutRes List<Integer> layoutXMLList);
    }

    public interface ViewIDSourceIntakeStep {
        PageSizeStep fromViewID(Activity activity, @IdRes Integer... xmlResourceList);

        PageSizeStep fromViewIDList(Activity activity, @IdRes List<Integer> xmlResourceList);

    }


    public interface PageSizeStep {
        FileNameStep setDefaultPageSize(PageSize pageSize);

        /**
         * Need to improvement.
         *
         * @param widthInPX
         * @param heightInPX
         * @return
         */
        FileNameStep setCustomPageSize(int widthInPX, int heightInPX);
    }


    public interface FileNameStep {
        Build setFileName(String fileName);
    }

    public interface Build {
        void build(PdfGeneratorListener pdfGeneratorListener);

        Build setFolderName(String folderName);

        Build openPDFafterGeneration(boolean open);

    }


    public static class Builder implements Build, FileNameStep, PageSizeStep
            , LayoutXMLSourceIntakeStep, ViewSourceIntakeStep, ViewIDSourceIntakeStep
            , FromSourceStep, ContextStep {

        private static int NO_XML_SELECTED_YET = -1;
        private int pageWidthInPixel = a4WidthInPX;
        private int pageHeightInPixel = a4HeightInPX;
        private Context context;
        private PageSize pageSize;
        private PdfGeneratorListener pdfGeneratorListener;
        private List<View> viewList = new ArrayList<>();
        private String fileName;
        private String targetPdf;
        private boolean openPdfFile = true;
        private String folderName;
        private String directory_path;
        private Disposable disposable;
        private void postFailure(String errorMessage) {
            FailureResponse failureResponse = new FailureResponse(errorMessage);
            postLog(errorMessage);
            if (pdfGeneratorListener != null)
                pdfGeneratorListener.onFailure(failureResponse);
        }

        private void postFailure(Throwable throwable) {
            FailureResponse failureResponse = new FailureResponse(throwable);
            if (pdfGeneratorListener != null)
                pdfGeneratorListener.onFailure(failureResponse);
        }

        private void postLog(String logMessage) {
            if (pdfGeneratorListener != null)
                pdfGeneratorListener.showLog(logMessage);
        }

        private void postOnGenerationStart(){
            if(pdfGeneratorListener!=null)
                pdfGeneratorListener.onStartPDFGeneration();
        }

        private void postOnGenerationFinished(){
            if(pdfGeneratorListener!=null)
                pdfGeneratorListener.onFinishPDFGeneration();
        }
        private void postSuccess(PdfDocument pdfDocument, File file, int widthInPS, int heightInPS) {
            if (pdfGeneratorListener != null)
                pdfGeneratorListener.onSuccess(new SuccessResponse(pdfDocument, file, widthInPS, heightInPS));
        }

        private void openGeneratedPDF() {
            File file = new File(targetPdf);
            if (file.exists()) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                Uri uri = Uri.fromFile(file);
                intent.setDataAndType(uri, "*/*");
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                try {
                    context.startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    postFailure(e);
                }
            } else {
                String path = TextUtils.isEmpty(directory_path) ? "null" : directory_path;
                postFailure("PDF file is not existing in storage. Your Generated path is " + path);
            }
        }

        private void print() {

            try {
                if (context != null) {
                    PdfDocument document = new PdfDocument();

                    if (pageSize != null) {
                        if (pageSize == PageSize.A4) {
                            pageHeightInPixel = a4HeightInPX;
                            pageWidthInPixel = a4WidthInPX;
                        } else if (pageSize == PageSize.A5) {
                            pageHeightInPixel = a5HeightInPX;
                            pageWidthInPixel = a5WidthInPX;
                        } else if (pageSize == PageSize.WRAP_CONTENT) {
                            pageWidthInPixel = WRAP_CONTENT_WIDTH;
                            pageHeightInPixel = WRAP_CONTENT_HEIGHT;
                        }
                    } else {
                        postLog("Default page size is not found. Your custom page width is " +
                                pageWidthInPixel+" and custom page height is "+pageHeightInPixel);
                    }


                    if (viewList == null || viewList.size() == 0)
                        postLog("View list null or zero sized");
                    for (int i = 0; i < viewList.size(); i++) {
                        View content = viewList.get(i);

                        if (pageWidthInPixel == WRAP_CONTENT_HEIGHT && pageHeightInPixel == WRAP_CONTENT_WIDTH) {

                            content.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                            pageHeightInPixel = content.getMeasuredHeight();
                            pageWidthInPixel = content.getMeasuredWidth();
                        }
                            /*If page size is less then standard A4 size then assign it A4 size otherwise
                            the view will be messed up or so minimized that it will be not print in pdf*/
                            pageWidthInPixel = Math.max(pageWidthInPixel, a4WidthInPX);
                            pageHeightInPixel = Math.max(pageWidthInPixel, a4HeightInPX);


                        /*Convert page size from pixel into post script because PdfDocument takes
                         * post script as a size unit*/
                        pageHeightInPixel = (int) (pageHeightInPixel * postScriptThreshold);
                        pageWidthInPixel = (int) (pageWidthInPixel * postScriptThreshold);


                        content.measure(View.MeasureSpec.makeMeasureSpec(pageWidthInPixel, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
                        pageHeightInPixel = (Math.max(content.getMeasuredHeight(), a4HeightInPostScript));


                        PdfDocument.PageInfo pageInfo =
                                new PdfDocument.PageInfo.Builder((pageWidthInPixel), (pageHeightInPixel), i + 1).create();
                        PdfDocument.Page page = document.startPage(pageInfo);

                        content.layout(0, 0, pageWidthInPixel, pageHeightInPixel);
                        content.draw(page.getCanvas());

                        document.finishPage(page);

                        /*Finally invalidate it and request layout for restore the previous state
                        * of the view as like as the xml. Otherwise for generating PDF by view id,
                        * the main view is being messed up because this a view is not cloneable and
                        * being modified in the above view related tasks for printing PDF. */
                        content.invalidate();
                        content.requestLayout();

                    }

                    //This is for prevent crashing while opening generated PDF.
                    StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
                    StrictMode.setVmPolicy(builder.build());

                    setUpDirectoryPath(context);

                    if (TextUtils.isEmpty(directory_path)) {
                        postFailure("Environment.getExternalStorageDirectory() and " +
                                "context.getExternalFilesDir()" +
                                " is returning null");
                        return;
                    }


                    directory_path = directory_path + "/" + folderName + "/";


                    File file = new File(directory_path);
                    if (!file.exists()) {
                        if (!file.mkdirs()) {
                            postLog("Folder is not created." +
                                    "file.mkdirs() is returning false");
                        }
                        //Folder is made here
                    }

                    targetPdf = directory_path + fileName + ".pdf";

                    File filePath = new File(targetPdf);
                    //File is created under the folder but not yet written.

                    disposeDisposable();
                    postOnGenerationStart();
                    disposable = Completable.fromAction(() -> document.writeTo(new FileOutputStream(filePath)))
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .doFinally(() -> {
                                document.close();
                                disposeDisposable();
                                postOnGenerationFinished();
                            })
                            .subscribe(() -> {
                                postSuccess(document, filePath, pageWidthInPixel, pageHeightInPixel);
                                document.close();
                                if (openPdfFile) {
                                    openGeneratedPDF();
                                }
                            }, this::postFailure);


                } else {
                    postFailure("Context is null");
                }
            } catch (Exception e) {
                postFailure(e);
            }

        }


        private void disposeDisposable() {
            if (disposable != null && !disposable.isDisposed())
                disposable.dispose();
        }

        private void setUpDirectoryPath(Context context) {
            if (Environment.getExternalStorageDirectory() != null &&
                    !TextUtils.isEmpty(Environment.getExternalStorageDirectory().getPath())) {
                directory_path = Environment.getExternalStorageDirectory().getPath();
            }
            if (((android.os.Build.VERSION.SDK_INT >= 29) || TextUtils.isEmpty(directory_path)) &&
                    (context.getExternalFilesDir(null) != null &&
                            !TextUtils.isEmpty(context.getExternalFilesDir(null).getAbsolutePath()))) {
                directory_path = context.getExternalFilesDir(null).getAbsolutePath();
                postLog("Environment.getExternalStorageDirectory() is returning null or" +
                        " you are using Android API level 30+ which prevents to get external storage path" +
                        "instead of using storage scope. Using context.getExternalFilesDir(null) which is " +
                        "returning the absolute path - "+directory_path+"");
            }
        }

        @Override
        public void build(PdfGeneratorListener pdfGeneratorListener) {
            this.pdfGeneratorListener = pdfGeneratorListener;
            if (ContextCompat.checkSelfPermission(context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                print();
            } else {
                postLog("WRITE_EXTERNAL_STORAGE Permission is not given." +
                        " Permission taking popup (using https://github.com/Karumi/Dexter) is going " +
                        "to be shown");
                Dexter.withContext(context)
                        .withPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        .withListener(new PermissionListener() {
                            @Override
                            public void onPermissionGranted(PermissionGrantedResponse permissionGrantedResponse) {
                                print();
                            }

                            @Override
                            public void onPermissionDenied(PermissionDeniedResponse permissionDeniedResponse) {
                                postLog("WRITE_EXTERNAL_STORAGE Permission is denied by user.");
                            }

                            @Override
                            public void onPermissionRationaleShouldBeShown(PermissionRequest permissionRequest,
                                                                           PermissionToken permissionToken) {
                            }
                        })
                        .withErrorListener(error -> postLog("Error from Dexter (https://github.com/Karumi/Dexter) : " +
                                error.toString())).check();
            }

        }


        @Override
        public PageSizeStep fromView(View... viewArrays) {
            viewList = new ArrayList<>(Arrays.asList(viewArrays));
            return this;
        }

        @Override
        public PageSizeStep fromViewList(List<View> viewList) {
            this.viewList = viewList;
            return this;
        }


        @Override
        public Build openPDFafterGeneration(boolean openPdfFile) {
            this.openPdfFile = openPdfFile;
            return this;
        }


        @Override
        public FromSourceStep setContext(Context context) {
            this.context = context;
            return this;
        }

        @Override
        public FileNameStep setDefaultPageSize(PageSize pageSize) {
            this.pageSize = pageSize;
            return this;
        }

        @Override
        public FileNameStep setCustomPageSize(int widthInPX, int heightInPX) {
            this.pageWidthInPixel = widthInPX;
            this.pageHeightInPixel = heightInPX;
            return this;
        }

        @Override
        public Build setFolderName(String folderName) {
            this.folderName = folderName;
            return this;
        }

        @Override
        public Build setFileName(String fileName) {
            this.fileName = fileName;
            return this;
        }


        @Override
        public PageSizeStep fromViewID(Activity activity, @IdRes Integer... viewIDs) {
            viewList = Utils.getViewListFromID(activity, Arrays.asList(viewIDs));
            return this;
        }

        @Override
        public PageSizeStep fromViewIDList(Activity activity, List<Integer> viewIDList) {
            viewList = Utils.getViewListFromID(activity, viewIDList);
            return this;
        }


        @Override
        public PageSizeStep fromLayoutXML(@LayoutRes Integer... layouts) {
            viewList = Utils.getViewListFromLayout(context, pdfGeneratorListener, Arrays.asList(layouts));
            return this;
        }

        @Override
        public PageSizeStep fromLayoutXMLList(@LayoutRes List<Integer> layoutXMLList) {
            viewList = Utils.getViewListFromLayout(context, pdfGeneratorListener, layoutXMLList);
            return this;
        }

        @Override
        public LayoutXMLSourceIntakeStep fromLayoutXMLSource() {
            return this;
        }

        @Override
        public ViewIDSourceIntakeStep fromViewIDSource() {
            return this;
        }

        @Override
        public ViewSourceIntakeStep fromViewSource() {
            return this;
        }
    }


}
