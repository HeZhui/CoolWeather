package com.example.hzh.coolweather.gson;

import com.google.gson.annotations.SerializedName;

/**
 * Created by Administrator on 2019/8/22.
 */

public class Now {
    @SerializedName( "tmp" )
    public String temperature;

    @SerializedName( "cond" )
    public More more;

    public class More {
        @SerializedName( "txt" )
        public String info;
    }
}
