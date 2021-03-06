package de.ur.mi.android.adventurerun.helper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.example.adventurerun.R;

import de.ur.mi.android.adventurerun.data.Track;
import de.ur.mi.android.adventurerun.database.PrivateDatabaseTracks;

public class TrackAdapter extends ArrayAdapter<Track> {
	
	private ArrayList<Track> tracks;
	private Context context;
	
	private TrackListListener listener;
	
	private PrivateDatabaseTracks db;
	
	public TrackAdapter(Context context, ArrayList<Track> tracks, TrackListListener listener, PrivateDatabaseTracks db) {
		super(context, R.id.track_list, tracks);
		
		this.context = context;
		this.tracks = tracks;
		this.listener = listener;
		
		this.db = db;
	}
	
	@Override
	public View getView(final int position, View convertView, ViewGroup parent) {
		View v = convertView;
		
		if (v == null) {
			LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			v = inflater.inflate(R.layout.track_item, null);
		}
		
		final Track track = tracks.get(position);
		
		if (track != null) {
			TextView trackName = (TextView) v.findViewById(R.id.track_name);
			trackName.setText(track.getName());
			trackName.setOnClickListener(new OnClickListener () {

				@Override
				public void onClick(View v) {
					listener.onRaceViewStarted(position);	
				}
				
			});
			
			TextView timestamp = (TextView) v.findViewById(R.id.track_timestamp);
			timestamp.setText(formatTimestamp(track.getTimestamp()));
			timestamp.setOnClickListener(new OnClickListener() {
				
				@Override
				public void onClick(View v) {
					listener.onRaceViewStarted(position);
					
				}
			});
			
			View infoTrack = v.findViewById(R.id.button_details_track);
			infoTrack.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {
					listener.onTrackDetailViewStarted(position);
				}
				
			});
			
			View deleteTrack = v.findViewById(R.id.button_remove_track);
			deleteTrack.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {
					
					AlertDialog.Builder builder = new AlertDialog.Builder(context);
					builder.setTitle(R.string.button_track_delete_title);
					builder.setMessage(R.string.button_track_delete_message);
					builder.setCancelable(false);
					
					builder.setPositiveButton(R.string.button_ok, 
							new DialogInterface.OnClickListener() {
								
								@Override
								public void onClick(DialogInterface dialog, int which) {
									db.deleteTrack(track);
									notifyDataSetChanged();
									listener.onTrackDeleted();
								}
							});
					
					builder.setNegativeButton(R.string.button_cancel, 
							new DialogInterface.OnClickListener() {
								
								@Override
								public void onClick(DialogInterface dialog, int which) {
									
								}
							});

					builder.show();
				}
				
			});
		}
		
		return v;
	}

	private String formatTimestamp(long timestamp) {
		return new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(timestamp);
	}
}
