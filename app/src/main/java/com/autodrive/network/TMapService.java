package com.autodrive.network;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

/**
 * Created by jaewoncho on 2016. 9. 21..
 */
public interface TMapService {
    @GET("tmap/routes")
    Call<String> listRepos(@Path("user") String user);
}
