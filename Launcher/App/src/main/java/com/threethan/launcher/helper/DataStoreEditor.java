package com.threethan.launcher.helper;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
//import androidx.datastore.migrations.SharedPreferencesView;
import androidx.datastore.migrations.SharedPreferencesView;
import androidx.datastore.preferences.core.MutablePreferences;
import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.core.PreferencesKeys;
import androidx.datastore.preferences.rxjava3.RxPreferenceDataStoreBuilder;
import androidx.datastore.rxjava3.RxDataStore;

import java.io.File;
import java.security.InvalidParameterException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.functions.Consumer;

// Add the following to build.gradle (app) dependencies:
// implementation 'androidx.datastore:datastore-preferences-rxjava3:1.0.0'

/**
 *     This class implements both SharedPreferences, and SharedPreferences.Editor
 * <p>
 *     Most functions are implemented, and functions near-identically to their original implementations,
 *     but instead function using the newer {@link androidx.datastore.core.DataStore} in place of
 *     {@link SharedPreferences}.
 * <p>
 *     In many cases, this may just be a drop-in replacement. However, keep in mind that writes are
 *     asynchronous by default, so async write followed by a read may produce unexpected results.
 *     You may set 'asyncWrite' to false at any time to get back synchronous behaviours.
 * <p>
 *     Additional functions are provided for asynchronous reading and better handling of DataStores,
 *     which will be accessible when using this class directly, and make implementation of DataStore
 *     in Java much easier.
 * @see android.content.SharedPreferences
 * @see android.content.SharedPreferences.Editor
 * @see androidx.datastore.core.DataStore
 * @noinspection unused, UnusedReturnValue
 * , rawtypes */
public class DataStoreEditor implements SharedPreferences, SharedPreferences.Editor {
    private static final String TAG = "DataStoreEditor";
    private static final Map<String, RxDataStore<Preferences>> dataStoreByName = new HashMap<>();
    /** If true, all write operations will be done asynchronously.
     * If false, all write operations will be blocking. */
    public boolean asyncWrite = true;
    RxDataStore<Preferences> dataStoreRX;

    /** @noinspection rawtypes*/
    final static Class[] classes = new Class[]{
            String.class, Integer.class, Long.class,
            Float.class, Double.class, Boolean.class, Set.class};

    /**
     * Creates a new instance for the chosen DataStore of the given context
     * @param context Context from which to get the DataStore
     * @param name Name of the DataStore
     */
    public DataStoreEditor(Context context, String name) {
        dataStoreRX = getDataStore(context, name);
    }

    /**
     * Creates a new instance for the "default" DataStore of the given context
     * @param context Context from which to get the DataStore
     */
    public DataStoreEditor(Context context) {
        dataStoreRX = getDataStore(context, "default");
    }

    /**
     * Creates a new instance to operate on a file
     * @param file The data store file to operate on (must already exist)
     */
    public DataStoreEditor(File file) {
        dataStoreRX = getDataStore(file);
    }
    synchronized private RxDataStore<Preferences> getDataStore(Context context, String name) {
        if (dataStoreByName.containsKey(name)) return dataStoreByName.get(name);
        RxDataStore<Preferences> ds = new RxPreferenceDataStoreBuilder(context, name).build();
        dataStoreByName.put(name, ds);
        return ds;
    }
    synchronized private RxDataStore<Preferences> getDataStore(File file) {
        final String name = file.getAbsolutePath();
        if (dataStoreByName.containsKey(name)) return dataStoreByName.get(name);
        RxDataStore<Preferences> ds = new RxPreferenceDataStoreBuilder(() -> file).build();
        dataStoreByName.put(name, ds);
        return ds;
    }

    /**
     * Migrates a specific sharedPreference instance to this DataStore
     * Data WILL be overridden!
     * You should include your own mechanism to avoid running this more than once.
     * @param sharedPreferences The sharedPreferences instance to migrate from
     * <p>
     * You may wish to set 'asyncWrite' to false before this operation.
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    public void migrateFrom(SharedPreferences sharedPreferences) {
        if (sharedPreferences instanceof DataStoreEditor)
            throw new InvalidParameterException(
                    "Cannot migrate from another instance of DataStoreEditor");
        SharedPreferencesView sharedPreferencesView
                = new SharedPreferencesView(sharedPreferences, sharedPreferences.getAll().keySet());
        Map<String, Object> allPrefs = sharedPreferencesView.getAll();
        allPrefs.forEach(this::putValue);
    }
    /**
     * Migrates default sharedPreferences to this DataStore (calls migrateFrom)
     * Data WILL be overridden!
     * You should include your own mechanism to avoid running this more than once.
     * You may wish to set 'asyncWrite' to false before this operation.
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    public void migrateDefault(Context context) {
        //noinspection deprecation
        migrateFrom(PreferenceManager.getDefaultSharedPreferences(context));
    }

    /**
     * Copies all preferences from another data store file
     * @param dataStoreFile The other file to copy from, must be valid .preferences_pb
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    public void copyFrom(File dataStoreFile) {
        DataStoreEditor other = new DataStoreEditor(dataStoreFile);
        getAll().forEach((key, o) -> removeValue(key, o.getClass(), true));
        other.getAll().forEach((key, o) -> putValue(key, o, true));
    }

    // Utility Functions
    /** @noinspection unchecked
     * Gets the key which matches the given type
     * @param key Name of the key
     * @param tClass Class of the value
     * @return The value itself
     * @param <T> Type of the value, should match tClass
     */
    public static  <T> Preferences.Key<T> getKey(String key, Class<T> tClass) {
        if (tClass == String.class ) return (Preferences.Key<T>) PreferencesKeys.stringKey   (key);
        if (tClass == Integer.class) return (Preferences.Key<T>) PreferencesKeys.intKey      (key);
        if (tClass == Long.class   ) return (Preferences.Key<T>) PreferencesKeys.longKey     (key);
        if (tClass == Float.class  ) return (Preferences.Key<T>) PreferencesKeys.floatKey    (key);
        if (tClass == Double.class ) return (Preferences.Key<T>) PreferencesKeys.doubleKey   (key);
        if (tClass == Boolean.class) return (Preferences.Key<T>) PreferencesKeys.booleanKey  (key);
        if (tClass == Set.class    ) return (Preferences.Key<T>) PreferencesKeys.stringSetKey(key);
        if (tClass.toString().contains("Set"))
            return (Preferences.Key<T>) PreferencesKeys.stringSetKey(key);
        throw new InvalidParameterException("Invalid preference class: "+tClass+
                ", must be one of "+ Arrays.toString(classes));
    }

    /** @noinspection unchecked
     * Gets the key which matches the type of the provided value
     * @param key Name of the key
     * @return The value itself
     * @param <T> Type of the value, should be derived automatically from val
     */
    protected static <T> Preferences.Key<T> getKey(String key, @NonNull T val) {
        if (val instanceof String ) return (Preferences.Key<T>) PreferencesKeys.stringKey   (key);
        if (val instanceof Integer) return (Preferences.Key<T>) PreferencesKeys.intKey      (key);
        if (val instanceof Long   ) return (Preferences.Key<T>) PreferencesKeys.longKey     (key);
        if (val instanceof Float  ) return (Preferences.Key<T>) PreferencesKeys.floatKey    (key);
        if (val instanceof Double ) return (Preferences.Key<T>) PreferencesKeys.doubleKey   (key);
        if (val instanceof Boolean) return (Preferences.Key<T>) PreferencesKeys.booleanKey  (key);
        if (val instanceof Set    ) return (Preferences.Key<T>) PreferencesKeys.stringSetKey(key);
        throw new InvalidParameterException("Invalid preference class, must be one of "
                + Arrays.toString(classes));
    }
    static Map<Class, Object> nullFallbacks = new HashMap<>();
    static {
        nullFallbacks.put(String.class, "<!NULL>");
        nullFallbacks.put(Integer.class, Integer.MIN_VALUE+1);
        nullFallbacks.put(Long.class, Long.MIN_VALUE+1);
        nullFallbacks.put(Float.class, Float.NaN);
        nullFallbacks.put(Double.class, Double.NaN);
        nullFallbacks.put(Boolean.class, Boolean.FALSE);
        nullFallbacks.put(Set.class, new NullStringSet());
    }
    private static class NullStringSet extends HashSet<String> {
        public NullStringSet() {}
        @Override
        public boolean equals(@Nullable Object o) {
            return o instanceof NullStringSet;
        }
    }

    /**
     * Synchronously gets the value of the given key
     * @param key The name of the key
     * @param def The default value, if none was found
     * @param tClass Class of the value
     * @return The value at the given key
     * @param <T> Type of the value, should be derived automatically from tClass
     */
    public <T> T getValue(String key, @Nullable T def, Class<T> tClass) {
        Preferences.Key<T> prefKey = getKey(key, tClass);
        //noinspection DataFlowIssue,unchecked
        @NonNull T nnDef = def==null ? (T) nullFallbacks.get(tClass) : def;
        if (def==null && tClass != Set.class) Log.w("DataStoreEditor",
                "null was passed as default value on a data store read, " +
                "this may not work as expected!\n A saved value of '" + nnDef +
                "' will be considered as equivalent to a nonexistent value for this operation.");
        @SuppressLint("UnsafeOptInUsageWarning")
        Single<T> value = dataStoreRX.data().firstOrError()
                .map(prefs -> prefs.get(prefKey)).onErrorReturn(throwable -> nnDef);
        if (def == null) {
            @NonNull T r = value.blockingGet();
            return (r == nullFallbacks.get(tClass) ? null : r);
        } else return value.blockingGet();
    }
    /**
     * Synchronously gets the value of the given key
     * @param key The name of the key
     * @param def The default value, if none was found
     * @return The value at the given key
     * @param <T> Type of the value, should be derived automatically from def
     */
    public <T> T getValue(String key, @NonNull T def) {
        Preferences.Key<T> prefKey = getKey(key, def);
        T nullFallback = null;
        @SuppressLint("UnsafeOptInUsageWarning")
        Single<T> value = dataStoreRX.data().firstOrError()
                .map(prefs -> prefs.get(prefKey)).onErrorReturn(throwable -> def);
        return value.blockingGet();
    }
    /**
     * Asynchronously gets the value of the given key
     * @param key The name of the key
     * @param def The default value, if none was found
     * @param tClass Class of the value
     * @param consumer Consumer which will be called with the value when its ready
     * @param <T> Type of the value, should be derived automatically from tClass
     */
    public <T> void getValue(String key, @Nullable T def, Consumer<T> consumer, Class<T> tClass) {
        Preferences.Key<T> prefKey = getKey(key, tClass);
        Single<T> value = dataStoreRX.data().firstOrError()
                .map(prefs -> prefs.get(prefKey)).onErrorReturn(throwable -> def);
        value.blockingSubscribe(consumer);
    }
    /**
     * Asynchronously gets the value of the given key
     * @param key The name of the key
     * @param def The default value, if none was found
     * @param consumer Consumer which will be called with the value when its ready
     * @param <T> Type of the value, should be derived automatically from def
     */
    public <T> void getValue(String key, @NonNull T def, Consumer<T> consumer) {
        Preferences.Key<T> prefKey = getKey(key, def);
        Single<T> value = dataStoreRX.data().firstOrError()
                .map(prefs -> prefs.get(prefKey)).onErrorReturn(throwable -> def);
        value.blockingSubscribe(consumer);
    }
    /**
     * Asynchronously writes a value which matches the given class
     * @param key Name of key to write to
     * @param value Value to write
     * @param tClass Class of the value
     * @param <T> Type of the value, should be derived automatically from tClass
     */
    public <T> void putValue(String key, @Nullable T value, Class<T> tClass) {
        boolean returnvalue;
        Preferences.Key<T> prefKey = getKey(key, tClass);
        Single<Preferences> updateResult =  dataStoreRX.updateDataAsync(prefsIn -> {
            MutablePreferences mutablePreferences = prefsIn.toMutablePreferences();
            mutablePreferences.set(prefKey, value);
            return Single.just(mutablePreferences);
        });
    }
    /**
     * Writes a value which matches the given class
     * @param key Name of key to write to
     * @param value Value to write
     * @param tClass Class of the value
     * @param <T> Type of the value, should be derived automatically from tClass
     */
    public <T> void putValue(String key, @Nullable T value, Class<T> tClass, boolean synchronous) {
        boolean returnvalue;
        Preferences.Key<T> prefKey = getKey(key, tClass);
        Single<Preferences> updateResult =  dataStoreRX.updateDataAsync(prefsIn -> {
            MutablePreferences mutablePreferences = prefsIn.toMutablePreferences();
            mutablePreferences.set(prefKey, value);
            return Single.just(mutablePreferences);
        });
        if (synchronous) { Preferences ignored = updateResult.blockingGet(); }
    }

    /**
     * Asynchronously writes a value which matches the given class
     * @param key Name of key to write to
     * @param value Value to write
     * @param <T> Type of the value, should be derived automatically from value
     */
    public <T> void putValue(String key, @NonNull T value) {
        putValue(key, value, false);
    }
    /**
     * Writes a value which matches the given class
     * @param key Name of key to write to
     * @param value Value to write
     * @param <T> Type of the value, should be derived automatically from value
     */
    public <T> void putValue(String key, @NonNull T value, boolean synchronous) {
        boolean returnvalue;
        Preferences.Key<T> prefKey = getKey(key, value);
        Single<Preferences> updateResult =  dataStoreRX.updateDataAsync(prefsIn -> {
            MutablePreferences mutablePreferences = prefsIn.toMutablePreferences();
            mutablePreferences.set(prefKey, value);
            return Single.just(mutablePreferences);
        });
        if (synchronous) { Preferences ignored = updateResult.blockingGet(); }
    }

    /**
     * Asynchronously removes a value which matches the given class
     * @param key Name of the key to remove
     * @param tClass Class of the value
     * @param <T> Type of the value, should be derived automatically from tClass
     */
    public <T> void removeValue(String key, Class<T> tClass) {
        removeValue(key, tClass, false);
    }
    /**
     * Removes a value which matches the given class
     * @param key Name of the key to remove
     * @param tClass Class of the value
     * @param <T> Type of the value, should be derived automatically from tClass
     */
    public <T> void removeValue(String key, Class<T> tClass, boolean synchronous){
        boolean returnvalue;
        Preferences.Key<T> prefKey = getKey(key, tClass);
        @SuppressLint("UnsafeOptInUsageWarning")
        Single<Preferences> updateResult =  dataStoreRX.updateDataAsync(prefsIn -> {
            MutablePreferences mutablePreferences = prefsIn.toMutablePreferences();
            T remove = mutablePreferences.remove(prefKey);
            return Single.just(mutablePreferences);
        });
        if (synchronous) { Preferences ignored = updateResult.blockingGet(); }
    }

    /**
     * Asynchronously clears ALL data in the store
     */
    public DataStoreEditor clear(){
        boolean returnvalue;
        @SuppressLint("UnsafeOptInUsageWarning")
        Single<Preferences> updateResult =  dataStoreRX.updateDataAsync(prefsIn -> {
            MutablePreferences mutablePreferences = prefsIn.toMutablePreferences();
            mutablePreferences.clear();
            return Single.just(mutablePreferences);
        });
        return this;
    }

    // String
    /** Synchronously reads and returns a value */
    public String getString(String key, String def) {
        return getValue(key, def, String.class);
    }
    /** Asynchronously reads a value using a {@link Consumer} */
    public void getString(String key, @NonNull String def, Consumer<String> consumer) {
        getValue(key, def, consumer, String.class);
    }
    /** Asynchronously writes a value */
    public DataStoreEditor putString(String key, String value) {
        putValue(key, value, String.class);
        return this;
    }
    /** Asynchronously removes a value */
    public DataStoreEditor removeString(String key) {
        removeValue(key, String.class);
        return this;
    }
    // Int
    /** Synchronously reads and returns a value */
    public int getInt(String key, int def) {
        return getValue(key, def, Integer.class);
    }
    /** Asynchronously reads a value using a {@link Consumer} */
    public void getInt(String key, int def, Consumer<Integer> consumer) {
        getValue(key, def, consumer, Integer.class);
    }
    /** Asynchronously writes a value */
    public DataStoreEditor putInt(String key, int value) {
        putValue(key, value, Integer.class);
        return this;
    }
    /** Asynchronously removes a value */
    public DataStoreEditor removeInt(String key) {
        removeValue(key, Integer.class);
        return this;
    }
    // Long
    /** Synchronously reads and returns a value */
    public long getLong(String key, long def) {
        return getValue(key, def, Long.class);
    }
    /** Asynchronously reads a value using a {@link Consumer} */
    public void getLong(String key, long def, Consumer<Long> consumer) {
        getValue(key, def, consumer, Long.class);
    }
    /** Asynchronously writes a value */
    public DataStoreEditor putLong(String key, long value) {
        putValue(key, value, Long.class);
        return this;
    }
    /** Asynchronously removes a value */
    public DataStoreEditor removeLong(String key) {
        removeValue(key, Long.class);
        return this;
    }

    // Float
    /** Synchronously reads and returns a value */
    public float getFloat(String key, float def) {
        return getValue(key, def, Float.class);
    }
    /** Asynchronously reads a value using a {@link Consumer} */
    public void getFloat(String key, float def, Consumer<Float> consumer) {
        getValue(key, def, consumer, Float.class);
    }
    /** Asynchronously writes a value */
    public DataStoreEditor putFloat(String key, float value) {
        putValue(key, value, Float.class);
        return this;
    }
    /** Asynchronously removes a value */
    public DataStoreEditor removeFloat(String key) {
        removeValue(key, Float.class);
        return this;
    }

    // Double
    /** Synchronously reads and returns a value */
    public double getDouble(String key, Double def) {
        return getValue(key, def, Double.class);
    }
    /** Asynchronously reads a value using a {@link Consumer} */
    public void getDouble(String key, double def, Consumer<Double> consumer) {
        getValue(key, def, consumer, Double.class);
    }
    /** Asynchronously writes a value */
    public DataStoreEditor putDouble(String key, double value) {
        putValue(key, value, Double.class);
        return this;
    }
    /** Asynchronously removes a value */
    public DataStoreEditor removeDouble(String key) {
        removeValue(key, Double.class);
        return this;
    }

    // Boolean
    /** Synchronously reads and returns a value */
    public boolean getBoolean(String key, boolean def) {
        return getValue(key, def, Boolean.class);
    }
    /** Asynchronously reads a value using a {@link Consumer} */
    public void getBoolean(String key, boolean def, Consumer<Boolean> consumer) {
        getValue(key, def, consumer, Boolean.class);
    }
    /** Asynchronously writes a value */
    public DataStoreEditor putBoolean(String key, boolean value) {
        putValue(key, value, Boolean.class);
        return this;
    }
    /** Asynchronously removes a value */
    public DataStoreEditor removeBoolean(String key) {
        removeValue(key, Boolean.class);
        return this;
    }

    // String Set
    /**
     * Synchronously fetches a string set.
     * The set returned is made modifiable for consitency with the old sharedPreferences editor
     */
    public Set<String> getStringSet(String key, Set<String> def) {
        //noinspection unchecked
        return new HashSet<String>(getValue(key, def, Set.class));
    }

    /**
     * Asynchronously fetches a string set.
     * The set returned is unmodifiable!
     */
    public void getStringSet(String key, @NonNull Set<String> def, Consumer<Set<String>> consumer) {
        //noinspection unchecked,rawtypes
        getValue(key, def, (Consumer) consumer, Set.class);
    }
    /** Asynchronously writes a value */
    public DataStoreEditor putStringSet(String key, Set<String> value) {
        putValue(key, value, Set.class);
        return this;
    }
    /** Asynchronously removes a value */
    public DataStoreEditor removeStringSet(String key) {
        removeValue(key, Set.class);
        return this;
    }

    // Compat
    /**
     * This is included for compatiblity, but is slow and may not work as expected.
     * <p>
     * It's recommended to use typed remove functions instead.
     * @noinspection rawtypes
     */
    @Deprecated
    @Override
    public DataStoreEditor remove(String key) {
        for (Class tClass : classes) {
            //noinspection unchecked
            removeValue(key, tClass);
        }
        return this;
    }

    /*** This is included for compatiblity, but is slow!
     * <p>
     * It's recommented to instead try to read your specific typed value,
     * you can use a default of null and check for a non-null return value.
     */
    @Override
    public boolean contains(String key) {
        //noinspection rawtypes
        for (Class tClass : classes) {
            //noinspection unchecked
            if (getValue(key, null, tClass) != null) return true;
        }
        return false;
    }

    /**
     * Commit/apply is no longer needed, and does nothing.
     * Keep in mind that all write operations are performed asynchronously.
     */
    @Deprecated
    public boolean commit() {return false;}

    /**
     * Commit/apply is no longer needed, and does nothing.
     * Keep in mind that all write operations are performed asynchronously.
     */
    @Deprecated
    public void apply() {}

    // Compat (sharedpref)
    /**
     * This is included for compatiblity, but simply returns the same object,
     * which implements both SharedPreferences and SharedPreferences.Editor
     */
    @Override
    @Deprecated
    public DataStoreEditor edit() {
        return this;
    }

    /**
     * Not implemented, will throw an error!
     */
    @Override
    @Deprecated
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        throw new RuntimeException("This function cannot be called on DataStoreEditor!");
    }

    /**
     * Not implemented, will throw an error!
     */
    @Override
    @Deprecated
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        throw new RuntimeException("This function cannot be called on DataStoreEditor!");
    }

    /**
     * Not implemented, will throw an error!
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public Map<String, ?> getAll() {
        HashMap<String, Object> ret = new HashMap<>();
        getAllKeyVal().forEach((key, o) -> ret.put(key.toString(), o));
        return ret;
    }
    private Map<Preferences.Key<?>, ?> getAllKeyVal() {
        return dataStoreRX.data().blockingFirst().asMap();
    }
}

