package com.gromyk.mlkit;

import android.app.Application;

import com.microsoft.appcenter.AppCenter;
import com.microsoft.appcenter.analytics.Analytics;
import com.microsoft.appcenter.crashes.Crashes;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AppCenter.start(this, "7df404ec-5396-4555-b3af-ca44b28a897b", Analytics.class, Crashes.class);
    }
}
