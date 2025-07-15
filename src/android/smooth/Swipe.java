package smooth.plugins.cordova.swipe;

import android.graphics.Color;
import android.os.Handler;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.apache.cordova.*;
import org.apache.cordova.engine.SystemWebView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import smooth.plugins.cordova.toast.Toast;

public class Swipe extends CordovaPlugin {

    private SwipeRefreshLayout swipeLayout;
    private boolean isEnable;
    private boolean toastShow;
    private String toastText;
    private String jsAction;
    private int backgroundColor;
    private int distance;
    private int[] colors;

    private Toast toastPlugin;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        JSONObject options = args.optJSONObject(0);
        if (options == null) {
            callbackContext.error("Missing options object");
            return true;
        }

        switch (action) {
            case "initialize":
                initialize(options, callbackContext);
                break;
            case "enable":
            case "backgroundColor":
            case "colors":
            case "distance":
                enable(action, options, callbackContext);
                break;
            case "enableToast":
            case "textToast":
            case "jsAction":
                handleOption(action, options, callbackContext);
                break;
            default:
                callbackContext.error("Invalid action: " + action);
                return false;
        }

        return true;
    }

    private void initialize(JSONObject options, CallbackContext callbackContext) {
        cordova.getActivity().runOnUiThread(() -> {
            try {
                if (swipeLayout != null) {
                    callbackContext.success();
                    return;
                }

                PluginManager pluginManager = webView.getPluginManager();
                CordovaPlugin plugin = pluginManager.getPlugin("Toast");
                if (plugin instanceof Toast) {
                    toastPlugin = (Toast) plugin;
                }

                isEnable = options.optBoolean("isEnable", true);
                toastShow = options.optBoolean("toastShow", false);
                toastText = options.optString("toastText", "");
                jsAction = options.optString("jsAction", "");
                distance = options.optInt("distance", 100);
                backgroundColor = Color.parseColor(options.optString("backgroundColor", "#FFFFFF"));
                colors = convertHexColorsToColorInt(options.optJSONArray("colors"));

                SystemWebView webViews = (SystemWebView) webView.getView();
                ViewGroup parentLayout = (ViewGroup) webViews.getParent();

                swipeLayout = new SwipeRefreshLayout(cordova.getActivity());
                RelativeLayout relativeLayout = new RelativeLayout(cordova.getActivity());

                parentLayout.removeView(webViews);
                swipeLayout.addView(relativeLayout);
                relativeLayout.addView(webViews);

                LinearLayout.LayoutParams swipeParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT);
                swipeLayout.setLayoutParams(swipeParams);

                RelativeLayout.LayoutParams webViewParams = new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.MATCH_PARENT,
                        RelativeLayout.LayoutParams.MATCH_PARENT);
                webViews.setLayoutParams(webViewParams);

                setupSwipeRefreshLayout();
                parentLayout.addView(swipeLayout);

                callbackContext.success();
            } catch (Exception e) {
                callbackContext.error("Error initializing Swipe: " + e.getMessage());
            }
        });
    }

    private void enable(String action, JSONObject options, CallbackContext callbackContext) {
        cordova.getActivity().runOnUiThread(() -> {
            try {
                if (swipeLayout == null) {
                    callbackContext.error("SwipeRefreshLayout is not initialized");
                    return;
                }

                switch (action) {
                    case "enable":
                        isEnable = options.optBoolean("enable", isEnable);
                        break;
                    case "backgroundColor":
                        backgroundColor = Color.parseColor(options.optString("backgroundColor", "#FFFFFF"));
                        break;
                    case "colors":
                        colors = convertHexColorsToColorInt(options.optJSONArray("colors"));
                        break;
                    case "distance":
                        distance = options.optInt("distance", distance);
                        break;
                }

                setupSwipeRefreshLayout();
                callbackContext.success();
            } catch (Exception e) {
                callbackContext.error("Error in enable: " + e.getMessage());
            }
        });
    }

    private void handleOption(String action, JSONObject options, CallbackContext callbackContext) {
        try {
            switch (action) {
                case "enableToast":
                    toastShow = options.optBoolean("enable", toastShow);
                    break;
                case "textToast":
                    toastText = options.optString("toastText", toastText);
                    break;
                case "jsAction":
                    jsAction = options.optString("jsAction", jsAction);
                    break;
            }
            callbackContext.success();
        } catch (Exception e) {
            callbackContext.error("Error in handleOption: " + e.getMessage());
        }
    }

    private final SwipeRefreshLayout.OnRefreshListener swipeListener = () -> {
        if (swipeLayout == null) return;

        swipeLayout.setRefreshing(true);

        if (toastShow) {
            showToast(toastText);
        }

        if (!jsAction.isEmpty()) {
            webView.loadUrl("javascript:" + jsAction);
        }

        new Handler().postDelayed(() -> {
            if (swipeLayout.isRefreshing()) {
                swipeLayout.setRefreshing(false);
            }
        }, 1000);
    };

    private void showToast(String text) {
        if (toastPlugin == null || text == null || text.trim().isEmpty()) return;

        cordova.getActivity().runOnUiThread(() -> {
            try {
                JSONObject opt = new JSONObject();
                opt.put("message", text);
                opt.put("position", "bottom");
                opt.put("duration", "short");

                JSONArray args = new JSONArray().put(opt);
                toastPlugin.execute("show", args, null);
            } catch (Exception e) {
                LOG.e("SWIPE_PLUGIN", "Exception in showToast", e);
            }
        });
    }

    private int[] convertHexColorsToColorInt(JSONArray hexColors) throws JSONException {
        if (hexColors == null || hexColors.length() == 0) {
            return new int[]{Color.BLUE, Color.GREEN, Color.RED}; // default
        }

        int[] outColors = new int[hexColors.length()];
        for (int i = 0; i < hexColors.length(); i++) {
            outColors[i] = Color.parseColor(hexColors.getString(i));
        }
        return outColors;
    }

    private void setupSwipeRefreshLayout() {
        swipeLayout.setOnRefreshListener(swipeListener);
        swipeLayout.setEnabled(isEnable);
        swipeLayout.setColorSchemeColors(colors);
        swipeLayout.setProgressBackgroundColorSchemeColor(backgroundColor);
        swipeLayout.setDistanceToTriggerSync(distance);

        SystemWebView webViews = (SystemWebView) webView.getView();
        webViews.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            swipeLayout.setEnabled(scrollY == 0 && isEnable);
        });
    }
}
