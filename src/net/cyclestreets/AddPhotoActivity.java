package net.cyclestreets;

import net.cyclestreets.api.ApiClient;
import net.cyclestreets.api.PhotomapCategories;
import net.cyclestreets.api.ICategory;
import net.cyclestreets.views.CycleMapView;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.RelativeLayout.LayoutParams;

import java.util.List;

public class AddPhotoActivity extends Activity 
							  implements View.OnClickListener
{
	private enum AddStep
	{
		PHOTO(null),
		CAPTION(PHOTO),
		CATEGORY(CAPTION),
		LOCATION(CATEGORY),
		DETAILS(LOCATION),
		SUBMIT(DETAILS);
		
		private AddStep(AddStep p)
		{
			prev_ = p;
			if(prev_ != null)
				prev_.next_ = this;
		} // AddStep

		public AddStep prev() { return prev_; }
		public AddStep next() { return next_; }
		
		private AddStep prev_;
		private AddStep next_;
	} // AddStep
	
	private AddStep step_;
	private Bitmap photo_ = null;
	private String caption_ = null;
	private int metaCat_ = -1;
	private int category_ = -1;
	
	private View photoView_;
	private View photoCaption_;
	private View photoCategory_;
	private View photoLocation_;
	
	private CycleMapView map_ = null;
	private static PhotomapCategories photomapCategories;
	
	
	@Override
	protected void onCreate(final Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);
		
		// start reading categories
		if(photomapCategories == null)
			new GetPhotomapCategoriesTask().execute();
		
		step_ = AddStep.PHOTO;

		LayoutInflater inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);

		photoView_ = inflater.inflate(R.layout.addphoto, null);
		photoCaption_ = inflater.inflate(R.layout.addphotocaption, null);
		photoCategory_ = inflater.inflate(R.layout.addphotocategory, null);
		photoLocation_ = inflater.inflate(R.layout.addphotolocation, null);
		map_ = new CycleMapView(this, this.getClass().getName());
		map_.enableAndFollowLocation();
		map_.getController().setZoom(map_.getMaxZoomLevel());
	
		final LinearLayout v = (LinearLayout)(photoLocation_.findViewById(R.id.mapholder));
		v.addView(map_, new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
		
		setupView();
	} // class AddPhotoActivity

	@Override 
	public void onBackPressed()
	{
		if(step_== AddStep.PHOTO)
		{
			super.onBackPressed();
			return;
		} // if ...
		
		step_ = step_.prev();
		setupView();
	} // onBackPressed
	

	private void nextStep()
	{
		switch(step_)
		{
		case CAPTION:
			caption_ = captionEditText().getText().toString();
			break;
		case CATEGORY:
			metaCat_ = metaCategorySpinner().getSelectedItemPosition();
			category_ = categorySpinner().getSelectedItemPosition();
			break;
		} // nextStep
		
		step_ = step_.next();
		setupView();
	} // nextStep
	
	private void setupView()
	{
		switch(step_)
		{
		case PHOTO:
			setContentView(photoView_);
			setupButtonListener(R.id.takephoto_button);
			setupButtonListener(R.id.chooseexisting_button);
			break;
		case CAPTION:
			setContentView(photoCaption_);
			if(caption_ != null)
				captionEditText().setText(caption_);
			break;
		case CATEGORY:
			setContentView(photoCategory_);
			try {
				if(photomapCategories == null)
					Thread.sleep(1000);
			}
			catch(Exception e) {
			}
						
			metaCategorySpinner().setAdapter(new CategoryAdapter(this, photomapCategories.metacategories));
			if(metaCat_ != -1)
				metaCategorySpinner().setSelection(metaCat_);
			categorySpinner().setAdapter(new CategoryAdapter(this, photomapCategories.categories));
			if(category_ != -1)
				categorySpinner().setSelection(category_);
			break;
		case LOCATION:
			setContentView(photoLocation_);
			break;
		} // switch ...
		
		previewPhoto();
		hookUpNext();
	} // setupView
	
	private void previewPhoto()
	{
		final ImageView iv = (ImageView)findViewById(R.id.photo);
		if(iv == null)
			return;
		iv.setImageBitmap(photo_);
		int newHeight = getWindowManager().getDefaultDisplay().getHeight() / 10 * 4;
		int newWidth = getWindowManager().getDefaultDisplay().getWidth();

		iv.setLayoutParams(new LinearLayout.LayoutParams(newWidth, newHeight));
		iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
	} // previewPhoto
	
	private void hookUpNext()
	{
		setupButtonListener(R.id.next);
	} // hookUpNext
	
	private EditText captionEditText() { return (EditText)findViewById(R.id.caption); }
	private Spinner metaCategorySpinner() { return (Spinner)findViewById(R.id.metacat); }
	private Spinner categorySpinner() { return (Spinner)findViewById(R.id.category); }
	
	private void setupButtonListener(int id)
	{
		final Button b = (Button)findViewById(id);
		if(b == null)
			return;
		
		b.setOnClickListener(this);		
	} // setupButtonListener
	
	@Override
	public void onClick(final View v) 
	{
		Intent i = null;
		
		switch(v.getId())
		{
			case R.id.takephoto_button:
				i = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);				
				break;
			case R.id.chooseexisting_button:
				i = new Intent(Intent.ACTION_PICK,
							   android.provider.MediaStore.Images.Media.INTERNAL_CONTENT_URI);
				break;
			case R.id.next:
				nextStep();
				break;
		} // switch
		
		if(i == null)
			return;
		
		startActivityForResult(i, v.getId());
	} // onClick
	
	@Override
	protected void onActivityResult(final int requestCode, 
									final int resultCode, 
									final Intent data) 
	{
        if (resultCode != RESULT_OK)
        	return;

        try {
        	final String photoPath = getImageFilePath(data);
        	if(photo_ != null)
        		photo_.recycle();
        	photo_ = BitmapFactory.decodeFile(photoPath);
		
        	nextStep();
        }
        catch(Exception e)
        {
        	Toast.makeText(this, "There was a problem grabbing the photo : " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
	} // onActivityResult
	
	private String getImageFilePath(final Intent data)
	{
        final Uri selectedImage = data.getData();
        final String[] filePathColumn = { MediaStore.Images.Media.DATA };

        final Cursor cursor = getContentResolver().query(selectedImage, filePathColumn, null, null, null);
        try {
        	cursor.moveToFirst();
        	return cursor.getString(cursor.getColumnIndex(filePathColumn[0]));
        } // try
        finally {
        	cursor.close();
        } // finally
	} // getImageFilePath

	private class GetPhotomapCategoriesTask extends AsyncTask<Object,Void,PhotomapCategories> 
	{
		protected PhotomapCategories doInBackground(Object... params) 
		{
			PhotomapCategories photomapCategories;
			try {
				photomapCategories = ApiClient.getPhotomapCategories();
			}
			catch (Exception ex) {
				throw new RuntimeException(ex);
			}
			return photomapCategories;
		} // PhotomapCategories
		
		@Override
		protected void onPostExecute(PhotomapCategories photomapCategories) 
		{
			AddPhotoActivity.photomapCategories = photomapCategories;
		} // onPostExecute
	} // class GetPhotomapCategoriesTask
	
	static private class CategoryAdapter extends BaseAdapter
	{
		private final LayoutInflater inflater_;
		private final List<?> list_;
		
		public CategoryAdapter(final Context context,
							   final List<?> list)
		{
			inflater_ = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			list_ = list;
		} // CategoryAdapter
		
		@Override
		public int getCount()
		{
			return list_.size();
		} // getCount
		
		@Override
		public String getItem(final int position)
		{
			final ICategory c = (ICategory)list_.get(position);
			return c.getName();
		} // getItem
		
		@Override
		public long getItemId(final int position)
		{
			return position;
		} // getItemId
		
		@Override
		public View getView(final int position, final View convertView, final ViewGroup parent)
		{
			final int id = (parent instanceof Spinner) ? android.R.layout.simple_spinner_item : android.R.layout.simple_spinner_dropdown_item;
			final TextView tv = (TextView)inflater_.inflate(id, parent, false);
			tv.setText(getItem(position));
			return tv;
		} // getView
	} // CategoryAdapter
	
} // class AddPhotoActivity
