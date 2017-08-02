package com.gmail.lindmik.satsapi;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
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
import org.threeten.bp.LocalDate;
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

        Spinner centerSpinner = findViewById(R.id.centersSpinner);
        centerSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                selectedCenter = (Center) adapterView.getAdapter().getItem(i);
                update();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

        List<LocalDate> dates = new ArrayList<>();
        LocalDate currentDate = LocalDate.now();
        for (int i = 0; i < 14; i++) {
            dates.add(currentDate.plusDays(i));
        }
        ArrayAdapter<LocalDate> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, dates);
        Spinner dateSpinner = findViewById(R.id.dateSpinner);
        dateSpinner.setAdapter(adapter);
        dateSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                selectedDate = ((LocalDate) adapterView.getAdapter().getItem(i));
                update();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

        queue.add(createCentersRequest());
    }

    private void update() {
        if (selectedDate != null && selectedCenter != null) {
            queue.add(createClassesRequest());
        }
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
            ((TextView) row.findViewById(R.id.startTime)).setText(formatDate(class1.getString("startTime")));
            ((TextView) row.findViewById(R.id.persons)).setText(String.format("%d/%d (%d)", class1.getInt("bookedPersonsCount"), class1.getInt("maxPersonsCount"), class1.getInt("waitingListCount")));
            classesTable.addView(row);
        }
    }

    private String formatDate(String dateTimeString) {
        return dateTimeString.substring(11, 16);
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
        Spinner centerSpinner = findViewById(R.id.centersSpinner);
        centerSpinner.setAdapter(new CenterSpinnerAdapter(this, android.R.layout.simple_spinner_item, centers));
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

    private static class Center {

        public String id;
        public String name;
        public Location location = new Location("");

        @Override
        public String toString() {
            return name;
        }
    }

    public class CenterSpinnerAdapter extends ArrayAdapter<Center> {

        private List<Center> centers;

        public CenterSpinnerAdapter(Context context, int textViewResourceId, List<Center> centers) {
            super(context, textViewResourceId, centers);
            this.centers = centers;
        }

        public int getCount() {
            return centers.size();
        }

        public Center getItem(int position) {
            return centers.get(position);
        }

        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            Center center = getItem(position);

            LayoutInflater inflater = getLayoutInflater();
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
