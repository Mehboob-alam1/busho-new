package com.mehboob.bushorat.fragments;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkError;
import com.android.volley.NoConnectionError;
import com.android.volley.ParseError;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.ServerError;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import com.mehboob.bushorat.R;
import com.mehboob.bushorat.adapters.RecyclerViewAdapter;
import com.mehboob.bushorat.models.Anime;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class Puzzle extends Fragment {

    private final String JSON_URL = "https://jobtanks.com/phpgame/api/product/puzzle.php";
    private JsonArrayRequest request;
    private RequestQueue requestQueue;
    private List<Anime> lstAnime;
    private RecyclerView recyclerView;
    ProgressBar loading;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_puzzle, container, false);

        loading = (ProgressBar)root.findViewById(R.id.bar);
        loading.setMax(100);
        lstAnime = new ArrayList<>();
        recyclerView = root.findViewById(R.id.recyclerviewid);

        jsonrequest();

        return root;
    }

    private void jsonrequest() {

        request = new JsonArrayRequest(JSON_URL, new Response.Listener<JSONArray>() {
            @Override
            public void onResponse(JSONArray response) {
                loading.setVisibility(View.GONE);
                JSONObject jsonObject  = null ;

                for (int i = 0 ; i < response.length(); i++ ) {

                    try {
                        jsonObject = response.getJSONObject(i);
                        Anime anime = new Anime() ;
                        anime.setWebsite_link(jsonObject.getString("puzzle_link"));
                        anime.setWebsite_logo(jsonObject.getString("puzzle_logo"));
                        lstAnime.add(anime);

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                setuprecyclerview(lstAnime);
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {

                if (error instanceof TimeoutError || error instanceof NoConnectionError) {
                    //This indicates that the request has either time out or there is no connection
                    Toast.makeText(getActivity(), "TimeOut...! No Internet",
                            Toast.LENGTH_LONG).show();

                } else if (error instanceof AuthFailureError) {
                    // Error indicating that there was an Authentication Failure while performing the request
                    Toast.makeText(getActivity(), "Failed To Receive Data",
                            Toast.LENGTH_LONG).show();

                } else if (error instanceof ServerError) {
                    //Indicates that the server responded with a error response
                    Toast.makeText(getActivity(), "Our Server Under Maintenance",
                            Toast.LENGTH_LONG).show();
                } else if (error instanceof NetworkError) {
                    //Indicates that there was network error while performing the request
                    Toast.makeText(getActivity(), "Network Not Responding",
                            Toast.LENGTH_LONG).show();
                } else if (error instanceof ParseError) {
                    // Indicates that the server response could not be parsed
                    Toast.makeText(getActivity(), "Please Reload Again!",
                            Toast.LENGTH_LONG).show();
                }
            }
        });

        requestQueue = Volley.newRequestQueue(getContext());
        requestQueue.add(request);
    }

    private void setuprecyclerview(List<Anime> lstAnime) {

        RecyclerViewAdapter myadapter = new RecyclerViewAdapter(getContext(),lstAnime);
        recyclerView.setAdapter(myadapter);

        // grid columns is 3
        GridLayoutManager manager = new GridLayoutManager(getContext(), 3);

        recyclerView.setAdapter(myadapter);
        recyclerView.setLayoutManager(manager);
    }

    public interface OnFragmentInteractionListener {
        // TODO: Update argument type and name
        void onFragmentInteraction(Uri uri);

    }
}
