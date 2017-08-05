package com.gmail.lindmik.satsapi;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.os.Bundle;
import android.support.v4.content.PermissionChecker;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.ListAdapter;
import android.widget.TableLayout;
import android.widget.TextView;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import com.jakewharton.threetenabp.AndroidThreeTen;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.threeten.bp.Instant;
import org.threeten.bp.LocalDate;
import org.threeten.bp.ZoneId;
import org.threeten.bp.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends Activity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private List<Center> centers = new ArrayList<>();

    private LocalDate selectedDate;
    private Center selectedCenter;

    private Location lastLocation;

    private RequestQueue queue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        queue = Volley.newRequestQueue(this);

        AndroidThreeTen.init(this);

        if (PermissionChecker.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationServices.getFusedLocationProviderClient(this).getLastLocation().addOnSuccessListener(this, new OnSuccessListener<Location>() {
                @Override
                public void onSuccess(Location location) {
                    Log.d(TAG, location.toString());
                    MainActivity.this.lastLocation = location;
                    sortCenters();
                }
            });
        }

        setDate(LocalDate.now());

        queue.add(createCentersRequest());
    }

    private void setCenter(Center center) {
        selectedCenter = center;
        Button centerButton = findViewById(R.id.centerButton);
        centerButton.setText(selectedCenter.name);
        fetchClasses();
    }

    private void setDate(LocalDate date) {
        selectedDate = date;
        Button dateButton = findViewById(R.id.dateButton);
        dateButton.setText(selectedDate.toString());
        fetchClasses();
    }

    private void fetchClasses() {
        if (selectedDate == null) {
            return;
        }

        if (selectedCenter == null) {
            return;
        }

        queue.add(createClassesRequest());
    }

    private JsonObjectRequest createClassesRequest() {
        return new JsonObjectRequest("https://www.elixia.fi/sats-api/fi/classes?centers=" + selectedCenter.id + "&dates=" + selectedDate.format(DateTimeFormatter.ofPattern("yyyyMMdd")), null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        Log.d(TAG, response.toString());
                        try {
                            handleClassesResponse(response);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e(TAG, "Error: " + error);
            }
        });
    }

    private JsonObjectRequest createCentersRequest() {
        return new JsonObjectRequest("https://www.elixia.fi/sats-api/fi/centers", null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        Log.d(TAG, response.toString());
                        try {
                            handleCentersResponse(response);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e(TAG, "Error: " + error);
            }
        });
    }

    private void handleClassesResponse(JSONObject response) throws JSONException {
        TableLayout classesTable = findViewById(R.id.classesTable);
        classesTable.removeAllViews();
        JSONArray classesArray = response.getJSONArray("classes");
        for (int i = 0; i < classesArray.length(); i++) {
            JSONObject class1 = (JSONObject) classesArray.get(i);
            View row = LayoutInflater.from(this).inflate(R.layout.class_row, null);
            ((TextView) row.findViewById(R.id.name)).setText(class1.getString("name"));
            ((TextView) row.findViewById(R.id.duration)).setText(String.format("%d min", class1.getInt("durationInMinutes")));
            ((TextView) row.findViewById(R.id.instructor)).setText(class1.getString("instructorId"));
            ((TextView) row.findViewById(R.id.startTime)).setText(DateTimeFormatter.ISO_DATE_TIME.parse(class1.getString("startTime"), Instant.FROM).atZone(ZoneId.systemDefault()).toLocalTime().toString());
            ((TextView) row.findViewById(R.id.persons)).setText(String.format("%d/%d (%d)", class1.getInt("bookedPersonsCount"), class1.getInt("maxPersonsCount"), class1.getInt("waitingListCount")));
            classesTable.addView(row);
        }
    }

    private void handleCentersResponse(JSONObject response) throws JSONException {
        centers.clear();
        JSONArray regionsArray = response.getJSONArray("regions");
        for (int i = 0; i < regionsArray.length(); i++) {
            JSONObject region = (JSONObject) regionsArray.get(i);
            JSONArray centersArray = region.getJSONArray("centers");
            for (int j = 0; j < centersArray.length(); j++) {
                JSONObject centerJsonObject = (JSONObject) centersArray.get(j);
                Center center = new Center();
                center.id = centerJsonObject.getString("id");
                center.name = centerJsonObject.getString("name");
                center.location.setLatitude(centerJsonObject.getDouble("lat"));
                center.location.setLongitude(centerJsonObject.getDouble("long"));
                centers.add(center);
            }
        }
        sortCenters();
        setCenter(centers.get(0));
    }

    private void sortCenters() {
        Collections.sort(centers, new Comparator<Center>() {
            @Override
            public int compare(Center center1, Center center2) {
                if (lastLocation != null) {
                    Float distance1 = lastLocation.distanceTo(center1.location);
                    Float distance2 = lastLocation.distanceTo(center2.location);
                    return distance1.compareTo(distance2);
                } else {
                    return center1.name.compareTo(center2.name);
                }
            }
        });
    }

    public void showCenterPickerDialog(View v) {
        DialogFragment newFragment = new CenterPickerFragment();
        newFragment.show(getFragmentManager(), "centerPicker");
    }

    public void showDatePickerDialog(View v) {
        DialogFragment newFragment = new DatePickerFragment();
        newFragment.show(getFragmentManager(), "datePicker");
    }

    private static class Center {

        public String id;
        public String name;
        public Location location = new Location("");

        @Override
        public String toString() {
            return name;
        }
    }

    public static class CenterPickerFragment extends DialogFragment implements DialogInterface.OnClickListener {

        private List<Center> centers;

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            this.centers = ((MainActivity) getActivity()).centers;
            Location lastLocation = ((MainActivity) getActivity()).lastLocation;
            return new AlertDialog.Builder(getActivity()).setAdapter(new CenterAdapter(getActivity(), centers, lastLocation), this).create();
        }

        @Override
        public void onClick(DialogInterface dialogInterface, int i) {
            ((MainActivity) getActivity()).setCenter(centers.get(i));
        }
    }

    public static class DatePickerFragment extends DialogFragment implements DatePickerDialog.OnDateSetListener {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the current date as the default date in the picker
            final LocalDate now = LocalDate.now();
            int year = now.getYear();
            int month = now.getMonthValue() - 1;
            int day = now.getDayOfMonth();

            // Create a new instance of DatePickerDialog and return it
            return new DatePickerDialog(getActivity(), this, year, month, day);
        }

        public void onDateSet(DatePicker view, int year, int month, int day) {
            ((MainActivity) getActivity()).setDate(LocalDate.of(year, month + 1, day));
        }
    }

    private static class CenterAdapter extends ArrayAdapter<CharSequence> {

        private Activity activity;

        private final List<Center> centers;
        private Location lastLocation;

        public CenterAdapter(Activity activity, List<Center> centers, Location lastLocation) {
            super(activity, R.layout.center_row);
            this.activity = activity;

            this.centers = centers;
            this.lastLocation = lastLocation;

            for (int i = 0; i < centers.size(); i++) {
                add(centers.get(i).name);
            }
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            Center center = centers.get(position);

            LayoutInflater inflater = activity.getLayoutInflater();
            View spView = inflater.inflate(R.layout.center_row, parent, false);

            TextView nameTextView = spView.findViewById(R.id.name);
            nameTextView.setText(center.name);

            TextView distanceTextView = spView.findViewById(R.id.distance);
            if (lastLocation != null) {
                Float distance = lastLocation.distanceTo(center.location);
                distanceTextView.setText(String.format("%.2f km", distance / 1000));
            } else {
                distanceTextView.setText("");
            }
            return spView;
        }
    }
}
