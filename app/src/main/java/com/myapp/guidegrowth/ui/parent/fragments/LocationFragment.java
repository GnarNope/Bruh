package com.myapp.guidegrowth.ui.parent.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.myapp.guidegrowth.R;
import com.myapp.guidegrowth.databinding.FragmentLocationBinding;
import com.myapp.guidegrowth.model.Child;
import com.myapp.guidegrowth.ui.parent.ParentDashboardActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

public class LocationFragment extends Fragment implements OnMapReadyCallback {

    private FragmentLocationBinding binding;
    private GoogleMap googleMap;
    private ListenerRegistration locationListener;
    private FirebaseFirestore db;
    private String childId;
    private String childName;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());

    public static LocationFragment newInstance(Child child) {
        LocationFragment fragment = new LocationFragment();

        if (child != null) {
            Bundle args = new Bundle();
            args.putString("childId", child.getId());
            args.putString("childName", child.getName());
            fragment.setArguments(args);
        }

        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = FirebaseFirestore.getInstance();

        if (getArguments() != null) {
            childId = getArguments().getString("childId");
            childName = getArguments().getString("childName");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentLocationBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Check if we have a valid child
        if (childId == null) {
            binding.noChildSelectedMessage.setVisibility(View.VISIBLE);
            binding.mapContainer.setVisibility(View.GONE);
            return;
        }

        // Initialize the map
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.map);

        if (mapFragment == null) {
            mapFragment = SupportMapFragment.newInstance();
            getChildFragmentManager()
                    .beginTransaction()
                    .replace(R.id.map, mapFragment)
                    .commit();
        }

        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // Setup refresh button
        binding.refreshLocationButton.setOnClickListener(v -> {
            requestLocationUpdate(childId);
            binding.refreshLocationButton.setEnabled(false);
            binding.locationProgressBar.setVisibility(View.VISIBLE);

            // Re-enable after 5 seconds
            binding.refreshLocationButton.postDelayed(() -> {
                if (isAdded()) {
                    binding.refreshLocationButton.setEnabled(true);
                }
            }, 5000);
        });

        // Start listening for location updates
        startLocationListener(childId);
    }

    @Override
    public void onMapReady(GoogleMap map) {
        googleMap = map;

        // Enable zoom controls
        googleMap.getUiSettings().setZoomControlsEnabled(true);

        // Default location (show until we get real data)
        LatLng defaultLocation = new LatLng(0, 0);
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 2));

        // If location data is available, it will be updated by the listener
    }

    private void startLocationListener(String childId) {
        if (childId == null) return;

        DocumentReference locationRef = db.collection("children")
                .document(childId)
                .collection("location")
                .document("current");

        locationListener = locationRef.addSnapshotListener((snapshot, e) -> {
            if (binding == null) return; // Fragment may be detached

            binding.locationProgressBar.setVisibility(View.GONE);

            if (e != null) {
                Toast.makeText(getContext(), "Error listening for location updates", Toast.LENGTH_SHORT).show();
                return;
            }

            if (snapshot != null && snapshot.exists()) {
                Map<String, Object> data = snapshot.getData();

                if (data != null) {
                    Double latitude = (Double) data.get("latitude");
                    Double longitude = (Double) data.get("longitude");
                    Object timestampObj = data.get("timestamp");

                    long timestamp;
                    if (timestampObj instanceof Timestamp) {
                        timestamp = ((Timestamp) timestampObj).toDate().getTime();
                    } else if (timestampObj instanceof Long) {
                        timestamp = (Long) timestampObj;
                    } else {
                        timestamp = System.currentTimeMillis();
                    }

                    if (latitude != null && longitude != null) {
                        updateMapWithLocation(latitude, longitude, childName, timestamp);

                        binding.noLocationDataMessage.setVisibility(View.GONE);
                        binding.mapContainer.setVisibility(View.VISIBLE);
                    }
                }
            } else {
                binding.noLocationDataMessage.setVisibility(View.VISIBLE);
                binding.mapContainer.setVisibility(View.GONE);
            }
        });
    }

    private void updateMapWithLocation(double latitude, double longitude, String name, long timestamp) {
        if (googleMap == null) return;

        LatLng location = new LatLng(latitude, longitude);

        // Clear previous markers
        googleMap.clear();

        // Add marker for current location
        googleMap.addMarker(new MarkerOptions()
                .position(location)
                .title(name != null ? name : "Child")
                .snippet("Last update: " + dateFormat.format(new Date(timestamp))));

        // Move camera to the location
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(location, 15));

        // Update the last seen text
        binding.lastLocationUpdateText.setText("Last seen: " + dateFormat.format(new Date(timestamp)));
    }

    private void requestLocationUpdate(String childId) {
        if (childId == null) return;

        if (getActivity() instanceof ParentDashboardActivity) {
            // Send check-in command to request immediate location update
            ((ParentDashboardActivity) getActivity()).sendCommandToChild("check_in", null);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // Remove location listener
        if (locationListener != null) {
            locationListener.remove();
        }

        binding = null;
    }
}