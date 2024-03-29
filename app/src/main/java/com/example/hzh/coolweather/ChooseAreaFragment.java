package com.example.hzh.coolweather;


import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.hzh.coolweather.db.City;
import com.example.hzh.coolweather.db.Country;
import com.example.hzh.coolweather.db.Province;
import com.example.hzh.coolweather.util.HttpUtil;
import com.example.hzh.coolweather.util.Utility;

import org.litepal.crud.DataSupport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class ChooseAreaFragment extends Fragment {
    private static final int LEVER_PROVINCE = 0;
    private static final int LEVER_CITY = 1;
    private static final int LEVER_COUNTRY = 2;

    private ProgressDialog progressDialog;

    private TextView titleText;
    private Button backButton;
    private ListView listView;

    private ArrayAdapter<String> adapter;
    private List<String> dataList = new ArrayList<>(  );

    /**
     * 省列表
     */
    private List<Province> provinceList;

    /**
     * 市列表
     */
    private List<City> cityList;

    /**
     * 县列表
     */
    private List<Country> countryList;

    /**
     * 选中的省份
     */
    private Province selectedProvince;

    /**
     * 选中的城市
     */
    private City selectedCity;

    /**
     * 当前选中的级别
     */
    private int currentLever;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate( R.layout.choose_area,container,false );
        titleText = (TextView)view.findViewById( R.id.title_text );
        backButton = (Button)view.findViewById( R.id.back_button);
        listView = (ListView)view.findViewById( R.id.list_view );
        adapter = new ArrayAdapter<String>( getContext(),android.R.layout.simple_list_item_1,dataList );//内置样式
        listView.setAdapter( adapter );
        return view;
    }

    @Override
    public void onActivityCreated( Bundle savedInstanceState) {
        super.onActivityCreated( savedInstanceState );
        //listView点击事件
        listView.setOnItemClickListener( new AdapterView.OnItemClickListener(){
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (currentLever == LEVER_PROVINCE){
                    selectedProvince = provinceList.get( position );
                    queryCities();
                }else if (currentLever == LEVER_CITY) {
                    selectedCity = cityList.get( position );
                    queryCountries();
                }else if (currentLever == LEVER_COUNTRY){//加载天气信息
                    String weatherId = countryList.get( position ).getWeatherId();
                    if (getActivity() instanceof MainActivity){//判断抽屉是从哪个活动打开的
                        Intent intent = new Intent( getActivity(),WeatherActivity.class );
                        intent.putExtra( "weather_id",weatherId );
                        startActivity( intent );
                        getActivity().finish();
                    }else if (getActivity() instanceof  WeatherActivity){
                        WeatherActivity weatherActivity = (WeatherActivity) getActivity();
                        weatherActivity.drawerLayout.closeDrawers();
                        weatherActivity.swipeRefresh.setRefreshing( true );//刷新天气信息
                        weatherActivity.requestWeather( weatherId );
                    }
                }
            }
        } );

        //返回按钮的点击事件
        backButton.setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick(View v) {
               if (currentLever == LEVER_COUNTRY){
                   queryCities();
               }else if (currentLever == LEVER_CITY){
                   queryProvinces();
               }
            }
        } );
        queryProvinces();//查询所有的省份
    }

    /**
     * 查询全国所有的省，优先从数据库查询，如果没有查询到再去服务器上查询
     */
    private void queryProvinces() {
        titleText.setText( "中国" );
        backButton.setVisibility( View.GONE );
        provinceList = DataSupport.findAll( Province.class );
        if (provinceList.size() > 0){
            dataList.clear();
            for (Province province : provinceList){
                dataList.add( province.getProvinceName() );
            }
            adapter.notifyDataSetChanged();
            listView.setSelection( 0 );//滑动到第一条数据
            currentLever = LEVER_PROVINCE;
        }else {
            String url = "http://guolin.tech/api/china";
            queryFromServer(url,"province");
        }
    }

    /**
     * 查询选中省的所有城市，优先从数据库查询，如果没有查询到再去服务器上查询
     */
    private void queryCities() {
        titleText.setText( selectedProvince.getProvinceName() );
        backButton.setVisibility( View.VISIBLE );
        cityList = DataSupport.where( "provinceid = ?",String.valueOf( selectedProvince.getId() ) ).find( City.class );//条件查询
        if (cityList.size() > 0){
            dataList.clear();
            for (City city : cityList){
                dataList.add( city.getCityName() );
            }
            adapter.notifyDataSetChanged();
            listView.setSelection( 0 );
            currentLever = LEVER_CITY;
        }else {
            int provinceCode = selectedProvince.getProvinceCode();
            String url = "http://guolin.tech/api/china/"+provinceCode;
            queryFromServer( url,"city" );
        }
    }

    /**
     * 查询选中市内的所有的县，优先从数据库查询，如果没有查询到再去服务器上查询
     */
    private void queryCountries() {
        titleText.setText( selectedCity.getCityName() );
        backButton.setVisibility( View.VISIBLE );
        countryList = DataSupport.where( "cityid = ?",String.valueOf( selectedCity.getId() ) ).find( Country.class );
        if (countryList.size() > 0){
            dataList.clear();
            for (Country country : countryList){
                dataList.add( country.getCountryName() );
            }
            adapter.notifyDataSetChanged();
            listView.setSelection( 0 );
            currentLever = LEVER_COUNTRY;
        }else {
            int provinceCode = selectedProvince.getProvinceCode();
            int cityCode = selectedCity.getCityCode();
            String url = "http://guolin.tech/api/china/"+provinceCode+"/"+cityCode;
            queryFromServer( url,"country" );
        }
    }

    /**
     * 根据传入的地址和类型从服务器上查询省市县数据
     * @param url
     * @param type
     */
    private void queryFromServer(String url, final String type) {
        showProgressDialog();
        HttpUtil.sendOkHttpRequest( url, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                //通过runOnUiThread()方法回到主线程处理逻辑
                getActivity().runOnUiThread( new Runnable() {
                    @Override
                    public void run() {
                        closeProgressDialog();
                        Toast.makeText( getContext(), "加载失败", Toast.LENGTH_SHORT ).show();
                    }
                } );
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseText = response.body().string();
                boolean result = false;
                if ("province".equals( type )){
                    result = Utility.handleProvinceResponse( responseText );
                }else if ("city".equals( type )){
                    result = Utility.handleCityResponse( responseText,selectedProvince.getId() );
                }else if ("country".equals( type )){
                    result = Utility.handleCountryResponse( responseText,selectedCity.getId() );
                }
                if (result){
                    getActivity().runOnUiThread( new Runnable() {
                        @Override
                        public void run() {
                            closeProgressDialog();
                            if ("province".equals( type )){
                                queryProvinces();
                            }else if ("city".equals( type )){
                                queryCities();
                            }else if ("country".equals( type )){
                                queryCountries();
                            }
                        }
                    } );
                }
            }
        } );
    }

    /**
     * 显示进度对话框
     */
    private void showProgressDialog() {
        if (progressDialog == null){
            progressDialog = new ProgressDialog( getActivity() );
            progressDialog.setMessage( "正在加载..." );
            progressDialog.setCanceledOnTouchOutside( false );
        }
    }

    /**
     * 关闭进度对话框
     */
    private void closeProgressDialog() {
        if (progressDialog != null){
            progressDialog.dismiss();
        }
    }

}
