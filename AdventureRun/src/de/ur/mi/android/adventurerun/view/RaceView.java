package de.ur.mi.android.adventurerun.view;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
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
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.adventurerun.R;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;

import de.ur.mi.android.adventurerun.control.RaceControl;
import de.ur.mi.android.adventurerun.data.Checkpoint;
import de.ur.mi.android.adventurerun.data.Track;
import de.ur.mi.android.adventurerun.database.PrivateDatabaseScores;
import de.ur.mi.android.adventurerun.database.PrivateDatabaseTracks;
import de.ur.mi.android.adventurerun.helper.Constants;
import de.ur.mi.android.adventurerun.helper.LocationController;
import de.ur.mi.android.adventurerun.helper.PositionListener;
import de.ur.mi.android.adventurerun.helper.RaceListener;

public class RaceView extends FragmentActivity implements RaceListener,
		PositionListener, SensorEventListener {

	private static final int COUNTDOWN_TIME = 5000;
	private static final int MAP_TIME_VISIBLE = 5000;
	private static final int COMPASS_UPDATE_TIME = 250;

	private RaceControl raceControl;
	private LocationController locationController;

	private SensorManager sensorManager;
	private Sensor magneticSensor;
	private Sensor accelerometerSensor;

	private GeomagneticField geoField;

	private Track currentTrack;
	private PrivateDatabaseTracks db;
	private PrivateDatabaseScores dbScores;
	private TextView textView_speed, textView_reachedCheckpoints,
			textView_distanceToCheckpoint, textView_distance,
			textView_remainingMapViews, textView_time;
	private Button buttonStart;
	private ImageView compass;

	private SupportMapFragment supportMapFragment;
	private GoogleMap map;

	private long lastUpdate;

	private Checkpoint currentCheckpoint;

	private ArrayList<CircleOptions> circles;

	private Location currentLocation, previousLocation;

	private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;

	private boolean raceStarted = false;
	private boolean gpsAvailable = false;

	private float[] valuesAccelerometer;
	private float[] valuesMagneticField;

	private float[] matrixR;
	private float[] matrixI;
	private float[] matrixValues;

	private double deviceOrientation;

	private float distance = 0;
	private int reachedCheckpoints = 0;

	private int remainingMapViews = 0;

	private long startTime;

	private static final float TEXT_SIZE_COUNTDOWN = 50;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.raceview);

		ActionBar actionBar = getActionBar();
		actionBar.setDisplayHomeAsUpEnabled(true);

		initDB();
		initSensorData();
		initSensor();
		getTrack();

		remainingMapViews = currentTrack.countCheckpoints();

		initController();
		initButtons();
		setupMap();
		initTextViews();

		actionBar.setTitle(currentTrack.getName());
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int itemId = item.getItemId();
		if (itemId == android.R.id.home) {
			finish();
		}
		return super.onOptionsItemSelected(item);
	}

	/**
	 * This method setups a Google Map with the Checkpoints of the current
	 * track.
	 */
	private void setupMap() {
		FragmentManager fmanager = getSupportFragmentManager();
		Fragment fragment = fmanager.findFragmentById(R.id.map_fragment);
		supportMapFragment = (SupportMapFragment) fragment;
		map = supportMapFragment.getMap();
		map.setMyLocationEnabled(true);
		map.animateCamera(CameraUpdateFactory.zoomTo(16));
		updateCheckpointsOnMap();
	}

	private void updateCheckpointsOnMap() {
		map.clear();
		ArrayList<Checkpoint> checkpoints = raceControl.getAllCheckpoints();
		circles = new ArrayList<CircleOptions>();

		for (Checkpoint c : checkpoints) {
			CircleOptions circle = new CircleOptions();
			LatLng latLng = new LatLng(c.getLatitude(), c.getLongitude());
			circle.center(latLng);
			circle.radius(c.getAccuracy());

			circle.fillColor(0x6024E35E);
			circle.strokeWidth(2);

			circles.add(circle);
			map.addCircle(circle);
		}
	}

	private void initTextViews() {
		textView_speed = (TextView) findViewById(R.id.textView_speed);
		textView_distanceToCheckpoint = (TextView) findViewById(R.id.textView_distance_to_checkpoint);
		textView_distance = (TextView) findViewById(R.id.textView_distance);
		textView_remainingMapViews = (TextView) findViewById(R.id.remaining_map_views);
		textView_time = (TextView) findViewById(R.id.textView_time);

		textView_remainingMapViews.append("" + remainingMapViews);
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
		dbScores = new PrivateDatabaseScores(this);

	}

	private void getTrack() {
		ArrayList<Track> tracks = new ArrayList<Track>();
		Bundle bundle = getIntent().getExtras();

		if (bundle.getInt(Constants.KEY_INTENT_TRACKVIEW) != -1) {
			int trackIndex = bundle.getInt(Constants.KEY_INTENT_TRACKVIEW);
			tracks = db.allTracks();
			currentTrack = tracks.get(trackIndex);
		}

	}

	private void initController() {
		raceControl = new RaceControl(this, currentTrack, this);
		locationController = new LocationController(this, this);
	}

	private void initButtons() {
		buttonStart = (Button) findViewById(R.id.button_start_run_track);
		compass = (ImageView) findViewById(R.id.imageView_compass);

		setOnClickListener();
	}

	private void setOnClickListener() {
		buttonStart.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if (raceStarted == false) {
					startCountdown();
				} else {
					informAboutRaceAborted();
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

					@Override
					public void onFinish() {
						buttonStart.setText(R.string.button_abort_run_track);
						raceControl.startRace();
						supportMapFragment.getView().setVisibility(
								View.INVISIBLE);
						compass.setClickable(true);
						startTime = System.currentTimeMillis();
						builder.dismiss();

					}
				}.start();

			}
		});

		compass.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if (remainingMapViews > 0) {
					remainingMapViews--;
					textView_remainingMapViews
							.setText(R.string.remaining_map_views);
					textView_remainingMapViews.append("" + remainingMapViews);
					compass.setClickable(false);
					supportMapFragment.getView().setVisibility(View.VISIBLE);
					new CountDownTimer(MAP_TIME_VISIBLE, 1000) {

						@Override
						public void onTick(long millisUntilFinished) {
						}

						@Override
						public void onFinish() {
							supportMapFragment.getView().setVisibility(
									View.INVISIBLE);
							if (remainingMapViews > 0) {
								compass.setClickable(true);
							} else {
								textView_remainingMapViews
										.setVisibility(View.INVISIBLE);
								TextView viewMapInfo = (TextView) findViewById(R.id.view_map_info);
								viewMapInfo
										.setText(R.string.no_remaining_map_views);
							}
						}

					}.start();
				}
			}

		});

		compass.setClickable(false);

	}

	private void adjustCompass() {
		if ((System.currentTimeMillis() - lastUpdate) >= COMPASS_UPDATE_TIME) {
			lastUpdate = System.currentTimeMillis();
			geoField = new GeomagneticField(Double.valueOf(
					currentLocation.getLatitude()).floatValue(), Double
					.valueOf(currentLocation.getLongitude()).floatValue(),
					Double.valueOf(currentLocation.getAltitude()).floatValue(),
					System.currentTimeMillis());

			compass.setRotation(0);
			compass.setRotation(raceControl.getBearing(currentLocation,
					currentCheckpoint) - (float) deviceOrientation);
		}
	}

	@Override
	public void onCheckpointReached() {
		Toast.makeText(this, "Checkpoint erreicht!", Toast.LENGTH_SHORT).show();
		updateCheckpointsOnMap();
		updateCheckpointsTextView();
	}

	private void updateCheckpointsTextView() {
		reachedCheckpoints++;
		Integer num = Integer.valueOf(reachedCheckpoints);
		String s = num.toString();
		textView_reachedCheckpoints = (TextView) findViewById(R.id.textView_reached_checkpoints_count);
		textView_reachedCheckpoints.setText(s);

	}

	@Override
	public void onRaceWon() {
		raceStarted = false;
		updateScoreList();
		setWinnerView();
	}

	private void updateScoreList() {
		long time = raceControl.getScore();
		dbScores.open();
		dbScores.updateScore(currentTrack, time);
		dbScores.close();

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

	private void informAboutRaceAborted() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.info_race_aborted_title);
		builder.setMessage(R.string.info_race_aborted_message);

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
	public void onNewLocation(Location location) {
		previousLocation = currentLocation;
		currentLocation = location;

		if (raceStarted == true) {
			currentCheckpoint = raceControl.getNextCheckpoint(currentLocation);

			raceControl.checkCheckpoint(currentLocation, currentCheckpoint);

			updateStatistics();

			adjustCompass();
		}
		updateCamera();
	}

	private void updateStatistics() {
		double latitudeCheckpoint = currentCheckpoint.getLatitude();
		double longitudeCheckpoint = currentCheckpoint.getLongitude();
		Location locationCheckpoint = new Location("Checkpoint");
		locationCheckpoint.setLatitude(latitudeCheckpoint);
		locationCheckpoint.setLongitude(longitudeCheckpoint);

		if (currentLocation != null) {
			distance += previousLocation.distanceTo(currentLocation);
		}

		textView_distance.setText(getString(R.string.raceview_distance)
				+ String.format("%.0f", distance) + "m");
		textView_speed.setText(getString(R.string.raceview_speed)
				+ String.format("%.1f", currentLocation.getSpeed() * 3.6)
				+ " km/h");
		textView_distanceToCheckpoint
				.setText(getString(R.string.raceview_distance_cp)
						+ String.format("%.0f",
								currentLocation.distanceTo(locationCheckpoint))
						+ "m");
	}

	private void updateCamera() {
		double latitude = currentLocation.getLatitude();
		double longitude = currentLocation.getLongitude();
		LatLng latLng = new LatLng(latitude, longitude);

		if (circles.size() < 2) {
			map.animateCamera(CameraUpdateFactory.newLatLng(latLng));
		} else {
			LatLngBounds.Builder builder = new LatLngBounds.Builder();
			builder.include(latLng);
			for (CircleOptions circle : circles) {
				builder.include(circle.getCenter());
			}
			LatLngBounds bounds = builder.build();

			CameraUpdate update = CameraUpdateFactory
					.newLatLngBounds(bounds, 5);
			map.animateCamera(update);
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
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.dialog_error_title);
		builder.setMessage(R.string.dialog_error_message + errorCode);

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
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case CONNECTION_FAILURE_RESOLUTION_REQUEST:
			switch (resultCode) {
			case Activity.RESULT_OK:
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
		if (raceStarted) {
			String time = new SimpleDateFormat("mm:ss").format(new Date(System
					.currentTimeMillis() - startTime));
			textView_time.setText(time);
		}

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

		if (currentLocation != null && currentCheckpoint != null) {
			adjustCompass();
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {

	}

}
