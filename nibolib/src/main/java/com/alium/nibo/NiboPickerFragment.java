package com.alium.nibo;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.annotation.DrawableRes;
import android.support.annotation.Nullable;
import android.support.annotation.RawRes;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.transition.TransitionManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.alium.nibo.models.NiboSelectedPlace;
import com.alium.nibo.repo.location.LocationAddress;
import com.alium.nibo.repo.location.LocationRepository;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.location.places.PlaceBuffer;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.GroundOverlay;
import com.google.android.gms.maps.model.GroundOverlayOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.cryse.widget.persistentsearch.DefaultVoiceRecognizerDelegate;
import org.cryse.widget.persistentsearch.RxPersistentSearchView;
import org.cryse.widget.persistentsearch.SearchItem;
import org.cryse.widget.persistentsearch.SearchSuggestionsBuilder;
import org.cryse.widget.persistentsearch.VoiceRecognitionDelegate;

import io.reactivex.annotations.NonNull;
import io.reactivex.functions.Consumer;

import static android.app.Activity.RESULT_OK;

/**
 * A placeholder fragment containing a simple view.
 */
public class NiboPickerFragment extends Fragment implements OnMapReadyCallback, GoogleMap.OnMarkerDragListener, GoogleApiClient.OnConnectionFailedListener, GoogleApiClient.ConnectionCallbacks {

    private static final int VOICE_RECOGNITION_REQUEST_CODE = 200;
    private GoogleMap mMap;
    private boolean hasWiderZoom;
    private Marker mCurrentMapMarker;
    private Address mGeolocation;
    private static final int DEFAULT_ZOOM = 16;
    private static final int WIDER_ZOOM = 6;
    private RelativeLayout mRootLayout;
    private RxPersistentSearchView mSearchView;
    private FloatingActionButton mCenterMyLocationFab;
    private LinearLayout mLocationDetails;
    private TextView mGeocodeAddress;

    private View mSearchTintView;

    private String TAG = getClass().getSimpleName();
    private GoogleApiClient mGoogleApiClient;
    private SearchSuggestionsBuilder mSamplesSuggestionsBuilder;

    private boolean isFirstLaunch = true;
    private String mSearchBarTitle;
    private String mConfirmButtonTitle;
    private NiboStyle mStyleEnum = NiboStyle.HOPPER;
    private TextView mPickLocationTextView;
    private
    @RawRes
    int mStyleFileID;
    private
    @DrawableRes
    int mMarkerPinIconRes;
    private NiboSelectedPlace mCurrentSelection;

    public NiboPickerFragment() {

    }

    public static NiboPickerFragment newInstance(String searchBarTitle, String confirmButtonTitle, NiboStyle styleEnum, @RawRes int styleFileID, @DrawableRes int markerIconRes) {
        Bundle args = new Bundle();
        NiboPickerFragment fragment = new NiboPickerFragment();
        args.putString(NiboConstants.SEARCHBAR_TITLE_ARG, searchBarTitle);
        args.putString(NiboConstants.SELECTION_BUTTON_TITLE, confirmButtonTitle);
        args.putSerializable(NiboConstants.STYLE_ENUM_ARG, styleEnum);
        args.putInt(NiboConstants.STYLE_FILE_ID, styleFileID);
        args.putInt(NiboConstants.MARKER_PIN_ICON_RES, markerIconRes);
        fragment.setArguments(args);
        return fragment;
    }

    public void setUpSearchView(boolean setUpVoice) {

        if (setUpVoice) {
            VoiceRecognitionDelegate delegate = new DefaultVoiceRecognizerDelegate(this, VOICE_RECOGNITION_REQUEST_CODE);
            if (delegate.isVoiceRecognitionAvailable()) {
                mSearchView.setVoiceRecognitionDelegate(delegate);
            }
        }

        // Hamburger has been clicked
        mSearchView.setHomeButtonListener(new RxPersistentSearchView.HomeButtonListener() {
            @Override
            public void onHomeButtonClick() {
                getActivity().finish();
            }
        });
        mSamplesSuggestionsBuilder = new PlaceSuggestionsBuilder(getActivity());

        mSearchView.setSuggestionBuilder(mSamplesSuggestionsBuilder);

        mSearchView.setSearchListener(new RxPersistentSearchView.SearchListener() {


            @Override
            public void onSearchEditOpened() {
                hideAddressWithTransition();

                //Use this to tint the screen
                if (mSearchTintView != null) {
                    mSearchTintView.setVisibility(View.VISIBLE);
                    mSearchTintView
                            .animate()
                            .alpha(1.0f)
                            .setDuration(300)
                            .setListener(new SimpleAnimationListener())
                            .start();
                }


            }

            @Override
            public void onSearchEditClosed() {
                if (mGeolocation != null) {
                    showAddressWithTransition();
                }

                if (mSearchTintView != null) {
                    mSearchTintView
                            .animate()
                            .alpha(0.0f)
                            .setDuration(300)
                            .setListener(new SimpleAnimationListener() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    super.onAnimationEnd(animation);
                                    mSearchTintView.setVisibility(View.GONE);
                                }
                            })
                            .start();
                }

            }


            @Override
            public boolean onSearchEditBackPressed() {
                if (mSearchView.isEditing()) {
                    mSearchView.cancelEditing();
                    return true;
                }
                return false;
            }

            @Override
            public void onSearchExit() {

            }

            @Override
            public void onSearchTermChanged(String term) {


            }

            @Override
            public void onSearch(String string) {

            }

            @Override
            public boolean onSuggestion(SearchItem searchItem) {
                Toast.makeText(getContext(), searchItem.getValue(), Toast.LENGTH_LONG).show();
                mSearchView.setSearchString(searchItem.getTitle(), true);
                mSearchView.setLogoText(searchItem.getTitle());
                getPlaceDetailsByID(searchItem.getValue());
                mSearchView.closeSearch();
                hideAddressWithTransition();
                return false;
            }

            @Override
            public void onSearchCleared() {

            }

        });

    }

    private void getPlaceDetailsByID(String placeId) {
        Places.GeoDataApi.getPlaceById(mGoogleApiClient, placeId)
                .setResultCallback(new ResultCallback<PlaceBuffer>() {
                    @Override
                    public void onResult(@android.support.annotation.NonNull PlaceBuffer places) {
                        if (places.getStatus().isSuccess()) {
                            if (places.getCount() > 0) {
                                setNewMapMarker(places.get(0).getLatLng());
                            }
                        }
                        places.release();
                    }
                });
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_picker_nibo, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initView(view);
        setUpBackPresses(view);
        initmap();

        Bundle args;
        if ((args = getArguments()) != null) {
            mSearchBarTitle = args.getString(NiboConstants.SEARCHBAR_TITLE_ARG);
            mConfirmButtonTitle = args.getString(NiboConstants.SELECTION_BUTTON_TITLE);
            mStyleEnum = (NiboStyle) args.getSerializable(NiboConstants.STYLE_ENUM_ARG);
            mMarkerPinIconRes = args.getInt(NiboConstants.MARKER_PIN_ICON_RES);
            mStyleFileID = args.getInt(NiboConstants.STYLE_FILE_ID);
        }

        if (mConfirmButtonTitle != null && !mConfirmButtonTitle.equals("")) {
            mPickLocationTextView.setText(mConfirmButtonTitle);
        }

        if (mSearchBarTitle != null && !mSearchBarTitle.equals("")) {
            mSearchView.setLogoText(mSearchBarTitle);
        }

        mGoogleApiClient = new GoogleApiClient
                .Builder(getActivity())
                .enableAutoManage(getActivity(), 0, this)
                .addApi(Places.GEO_DATA_API)
                .addApi(Places.PLACE_DETECTION_API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        mPickLocationTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.putExtra(NiboConstants.RESULTS_SELECTED, mCurrentSelection);
                getActivity().setResult(RESULT_OK, intent);
                getActivity().finish();
            }
        });

    }


    private void setUpBackPresses(View view) {
        view.setFocusableInTouchMode(true);
        view.requestFocus();
        view.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_BACK) {
                    // handle back button's click listener
                    if (mSearchView.isSearching()) {
                        mSearchView.closeSearch();
                    }
                    return true;
                }
                return false;
            }
        });
    }

    private void initmap() {
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        LocationRepository locationRepository = new LocationRepository(getActivity());

        locationRepository.getLocationObservable()
                .subscribe(new Consumer<Location>() {
                    @Override
                    public void accept(@NonNull Location location) throws Exception {
                        CameraPosition cameraPosition = new CameraPosition.Builder()
                                .target(new LatLng(location.getLatitude(), location.getLongitude()))
                                .zoom(15)
                                .build();
                        mMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
                        LatLng currentPosition = new LatLng(location.getLatitude(), location.getLongitude());
                        setNewMapMarker(currentPosition);

                        extractGeocode(location.getLatitude(), location.getLongitude());

                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(@NonNull Throwable throwable) throws Exception {
                        throwable.printStackTrace();
                    }
                });


    }

    private void extractGeocode(double lati, double longi) {
        if ((String.valueOf(lati).equals(null))) {
            Toast.makeText(getContext(), "Invlaid location", Toast.LENGTH_SHORT).show();
        } else {
            LocationAddress locationAddress = new LocationAddress();
            mGeolocation = locationAddress.getAddressFromLocation(lati, longi,
                    getContext());

            if (mGeolocation != null) {
                String fullAddress = getAddressHTMLText();
                mGeocodeAddress.setText(Html.fromHtml(fullAddress));
                showAddressWithTransition();
                Log.d("mGeolocation", " " + fullAddress);
                mCurrentSelection = new NiboSelectedPlace(new LatLng(lati, longi), null, getAddressString());
            }
        }
    }

    @android.support.annotation.NonNull
    private String getAddressHTMLText() {
        String address = mGeolocation.getAddressLine(0); // If any additional address line present than only, check with max available address lines by getMaxAddressLineIndex()
        String city = mGeolocation.getLocality();
        String state = mGeolocation.getAdminArea();
        String country = mGeolocation.getCountryName();
        String postalCode = mGeolocation.getPostalCode();

        String part1 = mGeolocation.getFeatureName();
        String part2 = address + ", " + city + ", " + state + ", " + country + ", " + postalCode;

        return "<b>" + part1 + "</b><label style='color:#ccc'> <br>" + part2 + "</label>";
    }

    @android.support.annotation.NonNull
    private String getAddressString() {
        String address = mGeolocation.getAddressLine(0); // If any additional address line present than only, check with max available address lines by getMaxAddressLineIndex()
        String city = mGeolocation.getLocality();
        String state = mGeolocation.getAdminArea();
        String country = mGeolocation.getCountryName();
        String postalCode = mGeolocation.getPostalCode();

        return address + ", " + city + ", " + state + ", " + country + ", " + postalCode;
    }


    public void addOverlay(LatLng place) {
        GroundOverlay groundOverlay = mMap.addGroundOverlay(new
                GroundOverlayOptions()
                .position(place, 100)
                .transparency(0.5f)
                .zIndex(3)
                .image(BitmapDescriptorFactory.fromBitmap(drawableToBitmap(getActivity().getResources().getDrawable(R.drawable.map_overlay)))));

        startOverlayAnimation(groundOverlay);
    }

    private Bitmap drawableToBitmap(Drawable drawable) {
        Bitmap bitmap = null;

        if (drawable instanceof BitmapDrawable) {
            BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
            if (bitmapDrawable.getBitmap() != null) {
                return bitmapDrawable.getBitmap();
            }
        }

        if (drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
            bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        } else {
            bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        }

        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }


    private void setNewMapMarker(LatLng latLng) {
        if (mMap != null) {
            if (mCurrentMapMarker != null) {
                mCurrentMapMarker.remove();
            }
            CameraPosition cameraPosition =
                    new CameraPosition.Builder().target(latLng)
                            .zoom(getDefaultZoom())
                            .build();
            hasWiderZoom = false;
            addOverlay(latLng);
            mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
            mCurrentMapMarker = addMarker(latLng);
            mMap.setOnMarkerDragListener(this);
            hideAddressWithTransition();
            extractGeocode(latLng.latitude, latLng.longitude);
        }
    }

    private int getDefaultZoom() {
        int zoom;
        if (hasWiderZoom) {
            zoom = WIDER_ZOOM;
        } else {
            zoom = DEFAULT_ZOOM;
        }
        return zoom;
    }

    private Marker addMarker(LatLng latLng) {
        if (getMarkerIconRes() != 0)
            return mMap.addMarker(new MarkerOptions().position(latLng).icon(BitmapDescriptorFactory.fromResource(getMarkerIconRes())).draggable(true));
        else
            return mMap.addMarker(new MarkerOptions().position(latLng).draggable(true));
    }

    protected
    @DrawableRes
    int getMarkerIconRes() {
        return mMarkerPinIconRes;
    }

    private void startOverlayAnimation(final GroundOverlay groundOverlay) {

        AnimatorSet animatorSet = new AnimatorSet();

        ValueAnimator vAnimator = ValueAnimator.ofInt(0, 100);
        vAnimator.setRepeatCount(ValueAnimator.INFINITE);
        vAnimator.setRepeatMode(ValueAnimator.RESTART);
        vAnimator.setInterpolator(new LinearInterpolator());
        vAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                final Integer val = (Integer) valueAnimator.getAnimatedValue();
                groundOverlay.setDimensions(val);
            }
        });

        ValueAnimator tAnimator = ValueAnimator.ofFloat(0, 1);
        tAnimator.setRepeatCount(ValueAnimator.INFINITE);
        tAnimator.setRepeatMode(ValueAnimator.RESTART);
        tAnimator.setInterpolator(new LinearInterpolator());
        tAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                Float val = (Float) valueAnimator.getAnimatedValue();
                groundOverlay.setTransparency(val);
            }
        });

        animatorSet.setDuration(3000);
        animatorSet.playTogether(vAnimator, tAnimator);
        animatorSet.start();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setMyLocationButtonEnabled(false);
        mMap.setMyLocationEnabled(true);
        mMap.setMaxZoomPreference(20);

        if (getMapStyle() != null)
            googleMap.setMapStyle(getMapStyle());

    }

    protected MapStyleOptions getMapStyle() {
        if (mStyleEnum == NiboStyle.CUSTOM) {
            if (mStyleFileID != 0) {
                return MapStyleOptions.loadRawResourceStyle(
                        getActivity(), mStyleFileID);
            } else {
                throw new IllegalStateException("NiboStyle.CUSTOM requires that you supply a custom style file, you can get one at https://snazzymaps.com/explore");
            }
        } else if (mStyleEnum == NiboStyle.DEFAULT) {
            return null;
        } else {
            if (mStyleEnum == null) {
                return null;
            }
            {
                return MapStyleOptions.loadRawResourceStyle(
                        getActivity(), mStyleEnum.getValue());
            }
        }
    }

    void showAddressWithTransition() {

        if (isFirstLaunch) {
            isFirstLaunch = false;
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            TransitionManager.beginDelayedTransition(mRootLayout);
        }
        mLocationDetails.setVisibility(View.VISIBLE);
        mMap.setPadding(0, 0, 0, mLocationDetails.getHeight());

    }


    void hideAddressWithTransition() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            TransitionManager.beginDelayedTransition(mRootLayout);
        }
        mLocationDetails.setVisibility(View.GONE);
        mMap.setPadding(0, 0, 0, 0);

    }


    private int dpToPx(int dp) {
        return (int) (dp * Resources.getSystem().getDisplayMetrics().density);
    }

    @Override
    public void onMarkerDragStart(Marker marker) {
        Vibrator myVib = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
        myVib.vibrate(50);
        hideAddressWithTransition();
    }

    @Override
    public void onMarkerDrag(Marker marker) {

    }

    @Override
    public void onMarkerDragEnd(Marker marker) {

        extractGeocode(marker.getPosition().latitude, marker.getPosition().longitude);
    }

    private void initView(View convertView) {
        this.mRootLayout = (RelativeLayout) convertView.findViewById(R.id.root_layout);
        this.mSearchView = (RxPersistentSearchView) convertView.findViewById(R.id.searchview);
        this.mCenterMyLocationFab = (FloatingActionButton) convertView.findViewById(R.id.center_my_location_fab);
        this.mLocationDetails = (LinearLayout) convertView.findViewById(R.id.location_details);
        this.mGeocodeAddress = (TextView) convertView.findViewById(R.id.geocode_address);
        this.mSearchTintView = convertView.findViewById(R.id.view_search_tint);
        this.mPickLocationTextView = (TextView) convertView.findViewById(R.id.pick_location_btn);

        mCenterMyLocationFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                initmap();
            }
        });

        setUpSearchView(true);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mGoogleApiClient != null)
            mGoogleApiClient.connect();
    }

    @Override
    public void onStop() {
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            ((PlaceSuggestionsBuilder) mSamplesSuggestionsBuilder).setGoogleApiClient(null);
            mGoogleApiClient.disconnect();
        }
        super.onStop();
    }

    @Override
    public void onConnected(Bundle bundle) {
        if (mSamplesSuggestionsBuilder != null)
            ((PlaceSuggestionsBuilder) mSamplesSuggestionsBuilder).setGoogleApiClient(mGoogleApiClient);
    }

    @Override
    public void onConnectionFailed(@android.support.annotation.NonNull ConnectionResult connectionResult) {

    }


    @Override
    public void onConnectionSuspended(int i) {

    }
}
