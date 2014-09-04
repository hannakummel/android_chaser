/* TODO
 * - LocationController vollst�ndig implementieren (bis jetzt checkForService drin)
 * - Proximity Alerts einsetzen
 */
//

package de.ur.mi.android.adventurerun.view;

import java.util.ArrayList;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.Color;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.adventurerun.R;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;

import de.ur.mi.android.adventurerun.control.RaceControl;
import de.ur.mi.android.adventurerun.control.RaceListener;
import de.ur.mi.android.adventurerun.data.Checkpoint;
import de.ur.mi.android.adventurerun.data.Track;
import de.ur.mi.android.adventurerun.database.PrivateDatabaseTracks;
import de.ur.mi.android.adventurerun.helper.Constants;
import de.ur.mi.android.adventurerun.helper.LocationController;
import de.ur.mi.android.adventurerun.helper.PositionListener;

public class RaceView extends FragmentActivity implements RaceListener,
		PositionListener, SensorEventListener {

	private static final int COUNTDOWN_TIME = 5000;
	
	private RaceControl raceControl;
	private LocationController locationController;

	private SensorManager sensorManager;
	private Sensor magneticSensor;
	private Sensor accelerometerSensor;

	private GeomagneticField geoField;

	private Track currentTrack;
	private PrivateDatabaseTracks db;
	private String trackName = "unknown";
	private TextView textViewTrackName, textView_speed, textView_distanceToCheckpoint, textView_distance;
	private Button buttonStart;

	private Checkpoint currentCheckpoint;

	private Location currentLocation;

	private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
	private static final String DIALOG_ERROR = "dialog_error";

	private boolean raceStarted = false;
	private boolean gpsAvailable = false;

	private float[] valuesAccelerometer;
	private float[] valuesMagneticField;

	private float[] matrixR;
	private float[] matrixI;
	private float[] matrixValues;

	private double deviceOrientation;
	
	private float distance = 0;

	private static final float TEXT_SIZE_COUNTDOWN = 50;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.raceview);

		ActionBar actionBar = getActionBar();
		
		checkForService();

		initDB();
		initSensorData();
		initSensor();
		getTrack();
		initController();
		initTextViews();
		initButtons();
		
		actionBar.setTitle(currentTrack.getName());
	}

	private void initTextViews() {
		textView_speed = (TextView) findViewById(R.id.textView_speed);
		textView_distanceToCheckpoint = (TextView) findViewById(R.id.textView_distance_to_checkpoint);
		textView_distance = (TextView) findViewById(R.id.textView_distance);
	}

	private void initSensorData() {
		valuesAccelerometer = new float[3];
		valuesMagneticField = new float[3];

		matrixR = new float[9];
		matrixI = new float[9];
		matrixValues = new float[3];

	}

	private void initSensor() {
		sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		magneticSensor = sensorManager
				.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
		accelerometerSensor = sensorManager
				.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
	}

	@Override
	public void onStart() {
		locationController.start();
		super.onStart();
	}

	@Override
	public void onStop() {
		locationController.stop();
		super.onStop();
	}

	@Override
	public void onPause() {
		locationController.pause();
		super.onPause();
		sensorManager.unregisterListener(this, accelerometerSensor);
		sensorManager.unregisterListener(this, magneticSensor);
	}

	@Override
	public void onResume() {
		locationController.resume();
		super.onResume();
		sensorManager.registerListener(this, magneticSensor,
				SensorManager.SENSOR_DELAY_NORMAL);
		sensorManager.registerListener(this, accelerometerSensor,
				SensorManager.SENSOR_DELAY_NORMAL);
	}

	private void initDB() {
		db = new PrivateDatabaseTracks(this);
		db.open();

	}

	private void getTrack() {
		ArrayList<Track> tracks = new ArrayList<Track>();
		Bundle bundle = getIntent().getExtras();

		if (bundle.getInt(Constants.KEY_INTENT_TRACKVIEW) != -1) {
			int trackIndex = bundle.getInt(Constants.KEY_INTENT_TRACKVIEW);
			tracks = db.allTracks();
			currentTrack = tracks.get(trackIndex);
			trackName = currentTrack.getName();
			textViewTrackName = (TextView) findViewById(R.id.textView_trackName);
			textViewTrackName.setText(trackName);
		}

	}

	private void initController() {
		raceControl = new RaceControl(this, currentTrack, this);
		locationController = new LocationController(this, this);
	}

	private void initButtons() {
		buttonStart = (Button) findViewById(R.id.button_start_run_track);

		setOnClickListener();

	}

	private void setOnClickListener() {
		buttonStart.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if (raceStarted == false) {
					startCountdown();
				} else {
					raceControl.stopRace();
				}

			}

			private void startCountdown() {
				final AlertDialog builder = new AlertDialog.Builder(
						RaceView.this).create();
				builder.setTitle(R.string.button_track_started_dialog_title);
				final TextView text = new TextView(RaceView.this);
				builder.setView(text);
				text.setTextColor(Color.RED);
				text.setTextSize(TEXT_SIZE_COUNTDOWN);
				text.setGravity(Gravity.CENTER_HORIZONTAL);
				builder.setCancelable(true);
				builder.show();

				new CountDownTimer(COUNTDOWN_TIME, 1000) {

					@Override
					public void onTick(long millisUntilFinished) {
						text.setText("" + (millisUntilFinished / 1000));
					}

					// Ein Piepton o.�. w�re an der Stelle super! ;)
					@Override
					public void onFinish() {
						buttonStart.setText(R.string.button_abort_run_track);
						raceControl.startRace();
						builder.dismiss();

					}
				}.start();

			}
		});

	}

	private void adjustCompass () { ImageView compass = (ImageView)
			findViewById(R.id.imageView_compass); compass.setRotation(0);
			compass.setRotation(raceControl.getBearing(currentLocation,
			currentCheckpoint) - (float) deviceOrientation);
	 }

	/*
	private void adjustCompass() {
		float[] orientation = sensorManager.getOrientation(matrixR,
				matrixValues);
		float heading = orientation[0];
		float bearing = raceControl.getBearing(currentLocation,
				currentCheckpoint);

		heading += geoField.getDeclination();
		heading = (bearing -  heading) * -1;

		if (heading < 0.0f || heading > 180.0f) {
			heading = 180 + ( 180 + heading);
		} 
		
		ImageView compass = (ImageView) findViewById(R.id.imageView_compass);
			
		Matrix matrix = new Matrix();
		compass.setScaleType(ScaleType.MATRIX);
		matrix.postRotate(heading, 100f, 100f);
		compass.setImageMatrix(matrix);
	}
	*/

	@Override
	public void onCheckpointReached() {
		// TextView information = (TextView)
		// findViewById(R.id.textView_race_information);
		// information.setText(R.string.textView_raceInformation_checkpointReached);
		Toast.makeText(this, "Checkpoint erreicht!", Toast.LENGTH_SHORT).show();

	}

	@Override
	public void onRaceWon() {
		raceStarted = false;
		setWinnerView();

	}

	private void setWinnerView() {
		TextView information = (TextView) findViewById(R.id.textView_race_information);
		information.setText(R.string.textView_raceInformation_raceWon);
		Button startRace = (Button) findViewById(R.id.button_start_run_track);
		startRace.setText(R.string.button_start_run_track);
		startRace.setEnabled(false);

	}

	@Override
	public void onRaceStarted() {
		raceStarted = true;
		buttonStart.setText(R.string.button_abort_run_track);
		locationController.startUpdates();
	}

	@Override
	public void onRaceStopped() {
		raceStarted = false;
		buttonStart.setText(R.string.button_start_run_track);

	}

	private boolean checkForService() {
		int resultCode = GooglePlayServicesUtil
				.isGooglePlayServicesAvailable(this);
		if (resultCode == ConnectionResult.SUCCESS) {
			Log.e("DEBUG", "Google Play Services available");
			return true;
		} else {
			ConnectionResult connectionResult = new ConnectionResult(
					resultCode, null);
			int errorCode = connectionResult.getErrorCode();
			Dialog errorDialog = GooglePlayServicesUtil.getErrorDialog(
					errorCode, this, CONNECTION_FAILURE_RESOLUTION_REQUEST);

			if (errorDialog != null) {
				ErrorDialogFragment errorFragment = new ErrorDialogFragment();
				errorFragment.setDialog(errorDialog);
				errorFragment.show(getSupportFragmentManager(),
						"Location Updates");
			}
			return false;
		}

	}

	@Override
	public void onNewLocation(Location location) {
		if (raceStarted == true) {
			if (currentLocation != null) {
				distance += currentLocation.distanceTo(location);
			}

			textView_distance.setText("D: " + distance);
			textView_speed.setText("S:" + location.getSpeed());
			
			currentLocation = location;
			currentCheckpoint = raceControl.getNextCheckpoint(currentLocation);
			raceControl.checkCheckpoint(currentLocation, currentCheckpoint);
			

			double latitudeCheckpoint = currentCheckpoint.getLatitude();
			double longitudeCheckpoint = currentCheckpoint.getLongitude();
			Location locationCheckpoint = new Location("Checkpoint");
			locationCheckpoint.setLatitude(latitudeCheckpoint);
			locationCheckpoint.setLongitude(longitudeCheckpoint);

			textView_distanceToCheckpoint.setText("N: " + location.distanceTo(locationCheckpoint));
			
			geoField = new GeomagneticField(Double.valueOf(
					location.getLatitude()).floatValue(), Double.valueOf(
					location.getLongitude()).floatValue(), Double.valueOf(
					location.getAltitude()).floatValue(),
					System.currentTimeMillis());
			
			adjustCompass();
		}
	}

	@Override
	public void onConnected() {
		Toast.makeText(this, "Connected", Toast.LENGTH_SHORT).show();

	}

	@Override
	public void onDisconnected() {
		Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show();

	}

	@Override
	public void onConnectionFailed(ConnectionResult result) {
		if (result.hasResolution()) {
			try {
				result.startResolutionForResult(this,
						CONNECTION_FAILURE_RESOLUTION_REQUEST);
			} catch (IntentSender.SendIntentException e) {
				e.printStackTrace();
			}
		} else {
			showErrorDialog(result.getErrorCode());
		}

	}

	private void showErrorDialog(int errorCode) {
		// Create a fragment for the error dialog
		ErrorDialogFragment dialogFragment = new ErrorDialogFragment();
		// Pass the error that should be displayed
		Bundle args = new Bundle();
		args.putInt(DIALOG_ERROR, errorCode);
		dialogFragment.setArguments(args);
		dialogFragment.show(getSupportFragmentManager(), "errordialog");
	}

	public static class ErrorDialogFragment extends DialogFragment {
		private Dialog dialog;

		public ErrorDialogFragment() {
			super();
			dialog = null;
		}

		public void setDialog(Dialog dialog) {
			this.dialog = dialog;
		}

		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			return dialog;
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case CONNECTION_FAILURE_RESOLUTION_REQUEST:
			switch (resultCode) {
			case Activity.RESULT_OK:
				// TRY REQUEST AGAIN
				break;
			}
		}
	}

	@Override
	public void onGPSDisabled() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.info_gps_title);
		builder.setMessage(R.string.info_gps_message);

		builder.setCancelable(false);

		builder.setPositiveButton(R.string.button_ok,
				new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {

					}
				});
		builder.show();

	}

	@Override
	public void onSensorChanged(SensorEvent event) {

		switch (event.sensor.getType()) {
		case Sensor.TYPE_ACCELEROMETER:

			for (int i = 0; i < 3; i++) {
				valuesAccelerometer[i] = event.values[i];
			}
			break;

		case Sensor.TYPE_MAGNETIC_FIELD:

			for (int i = 0; i < 3; i++) {
				valuesMagneticField[i] = event.values[i];
			}
			break;
		}

		boolean success = SensorManager.getRotationMatrix(matrixR, matrixI,
				valuesAccelerometer, valuesMagneticField);

		if (success) {
			SensorManager.getOrientation(matrixR, matrixValues);

			deviceOrientation = Math.toDegrees(matrixValues[0]);

		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO Auto-generated method stub

	}

}
