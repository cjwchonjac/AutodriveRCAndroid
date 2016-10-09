package com.autodrive.network;

import retrofit2.Retrofit;

/**
 * Created by jaewoncho on 2016. 9. 21..
 */
public class AutodriveHttpClient {
    private static volatile TMapService sTMapService;

    public static TMapService getTMapService() {
        if (sTMapService == null) {
            Retrofit retrofit = new Retrofit.Builder().baseUrl("https://apis.skplanetx.com/").build();
            sTMapService = retrofit.create(TMapService.class);
        }

        return sTMapService;
    }
}
