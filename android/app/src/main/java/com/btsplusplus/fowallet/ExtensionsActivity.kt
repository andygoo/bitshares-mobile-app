package com.btsplusplus.fowallet

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Point
import android.net.Uri
import android.support.design.widget.TabLayout
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import android.support.v4.view.ViewPager
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.Scroller
import android.widget.Toast
import bitshares.*
import com.btsplusplus.fowallet.kline.TradingPair
import com.fowallet.walletcore.bts.BitsharesClientManager
import com.fowallet.walletcore.bts.ChainObjectManager
import com.fowallet.walletcore.bts.WalletManager
import kotlinx.android.synthetic.main.bottom_nav.*
import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.Field


fun AppCompatActivity.setFullScreen() {
    val dector_view: View = window.decorView
    val option: Int = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_IMMERSIVE

    dector_view.systemUiVisibility = option
    window.navigationBarColor = Color.TRANSPARENT
}

fun AppCompatActivity.setBottomNavigationStyle(position: Int) {
    val color: Int = resources.getColor(R.color.theme01_textColorHighlight)
    when (position) {
        0 -> {
            bottom_nav_text_view_markets.setTextColor(color)
            bottom_nav_image_view_markets.setColorFilter(color)
        }
        1 -> {
            bottom_nav_text_view_diya.setTextColor(color)
            bottom_nav_image_view_diya.setColorFilter(color)
        }
        2 -> {
            bottom_nav_text_view_services.setTextColor(color)
            bottom_nav_image_view_services.setColorFilter(color)
        }
        3 -> {
            bottom_nav_text_view_my.setTextColor(color)
            bottom_nav_image_view_my.setColorFilter(color)
        }
    }
    bottom_nav_markets_frame.setOnClickListener { goTo(ActivityIndexMarkets::class.java) }
    bottom_nav_diya_frame.setOnClickListener { goTo(ActivityIndexCollateral::class.java) }
    bottom_nav_services_frame.setOnClickListener { goTo(ActivityIndexServices::class.java) }
    bottom_nav_my_frame.setOnClickListener { goTo(ActivityIndexMy::class.java) }
}

fun AppCompatActivity.clearBottomAllColor() {
    val default_color: Int = resources.getColor(R.color.theme01_textColorGray)
    //  文字
    bottom_nav_text_view_markets.setTextColor(default_color)
    bottom_nav_text_view_diya.setTextColor(default_color)
    bottom_nav_text_view_services.setTextColor(default_color)
    bottom_nav_text_view_my.setTextColor(default_color)
    //  图片
    bottom_nav_image_view_markets.setColorFilter(default_color)
    bottom_nav_image_view_diya.setColorFilter(default_color)
    bottom_nav_image_view_services.setColorFilter(default_color)
    bottom_nav_image_view_my.setColorFilter(default_color)
}

fun android.app.Activity.alerShowMessageConfirm(title: String?, message: String): Promise {
    return UtilsAlert.showMessageConfirm(this, title, message)
}

fun android.app.Activity.viewUserLimitOrders(account_id: String, tradingPair: TradingPair?) {
    //  [统计]
    fabricLogCustom("event_view_userlimitorders", jsonObjectfromKVS("account", account_id))

    val mask = ViewMesk(R.string.nameRequesting.xmlstring(this), this)
    mask.show()
    //  1、查账号数据
    val p1 = ChainObjectManager.sharedChainObjectManager().queryFullAccountInfo(account_id)

    //  2、帐号历史
    //  查询最新的 100 条记录。
    val stop = "1.${EBitsharesObjectType.ebot_operation_history.value}.0"
    val start = "1.${EBitsharesObjectType.ebot_operation_history.value}.0"
    //  start - 从指定ID号往前查询（包含该ID号），如果指定ID为0，则从最新的历史记录往前查询。结果包含 start。
    //  stop  - 指定停止查询ID号（结果不包含该ID），如果指定为0，则查询到最早的记录位置（or达到limit停止。）结果不包含该 stop ID。
    val conn = GrapheneConnectionManager.sharedGrapheneConnectionManager().any_connection()
    val p2 = conn.async_exec_history("get_account_history", jsonArrayfrom(account_id, stop, 100, start))

    //  查询全部
    Promise.all(p1, p2).then {
        val array_list = it as JSONArray

        val full_account_data = array_list.getJSONObject(0)
        val account_history = array_list.getJSONArray(1)

        //  限价单
        val asset_id_hash = JSONObject()
        val limit_orders = full_account_data.optJSONArray("limit_orders")
        if (limit_orders != null && limit_orders.length() > 0) {
            for (order in limit_orders) {
                val sell_price = order!!.getJSONObject("sell_price")
                asset_id_hash.put(sell_price.getJSONObject("base").getString("asset_id"), true)
                asset_id_hash.put(sell_price.getJSONObject("quote").getString("asset_id"), true)
            }
        }

        //  成交历史
        val tradeHistory = JSONArray()
        for (history in account_history) {
            val op = history!!.getJSONArray("op")
            val op_code = op.getInt(0)
            if (op_code == EBitsharesOperations.ebo_fill_order.value) {
                tradeHistory.put(history)
                val op_info = op.getJSONObject(1)
                asset_id_hash.put(op_info.getJSONObject("pays").getString("asset_id"), true)
                asset_id_hash.put(op_info.getJSONObject("receives").getString("asset_id"), true)
            }
        }

        //  查询 & 缓存
        return@then ChainObjectManager.sharedChainObjectManager().queryAllAssetsInfo(asset_id_hash.keys().toJSONArray()).then {
            mask.dismiss()
            goTo(ActivityMyOrders::class.java, true, args = jsonArrayfrom(full_account_data, tradeHistory, tradingPair
                    ?: "null"))
            return@then null
        }
    }.catch {
        mask.dismiss()
        showToast(resources.getString(R.string.nameNetworkException))
    }
}

fun android.app.Activity.viewUserAssets(account_name_or_id: String) {
    //  [统计]
    fabricLogCustom("event_view_userassets", jsonObjectfromKVS("account", account_name_or_id))

    val mask = ViewMesk(R.string.nameRequesting.xmlstring(this), this)
    mask.show()
    ChainObjectManager.sharedChainObjectManager().queryFullAccountInfo(account_name_or_id).then {
        val full_account_data = it as JSONObject
        val userAssetDetailInfos = OrgUtils.calcUserAssetDetailInfos(full_account_data)
        return@then ChainObjectManager.sharedChainObjectManager().queryAllAssetsInfo(userAssetDetailInfos.getJSONObject("validBalancesHash").keys().toJSONArray()).then {
            mask.dismiss()
            goTo(ActivityMyAssets::class.java, true, args = jsonArrayfrom(userAssetDetailInfos, full_account_data))
            return@then null
        }
    }.catch {
        mask.dismiss()
        showToast(resources.getString(R.string.nameNetworkException))
    }
}

fun android.app.Activity.runOnMainUI(body: () -> Unit) {
    this.runOnUiThread { body() }
}

/**
 * 隐藏软键盘
 */
fun android.app.Activity.hideSoftKeyboard() {
    val view = this.currentFocus
    if (view != null) {
        val mgr = this.getSystemService(android.app.Activity.INPUT_METHOD_SERVICE) as? InputMethodManager
        if (mgr != null) {
            mgr.hideSoftInputFromWindow(view.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
        }
    }
}

fun android.app.Activity.showToast(str: String, duration: Int = Toast.LENGTH_SHORT) {
    UtilsAlert.showToast(this.applicationContext, str, duration)
}

fun Fragment.showToast(str: String, duration: Int = Toast.LENGTH_SHORT) {
    this.activity?.showToast(str, duration)
}

/**
 * 显示石墨烯网络错误信息（部分错误特殊处理）
 */
fun android.app.Activity.showGrapheneError(error: Any?) {
    if (error != null) {
        try {
            var json: JSONObject
            if (error is Promise.WsPromiseException) {
                json = JSONObject(error.message.toString())
            } else {
                json = JSONObject(error.toString())
            }
            val msg = json.optString("message", "")
            if (msg != "") {
                //  特化错误信息
                //  "Assert Exception: account: no such account"
                if (msg.indexOf("no such account") > 0) {
                    showToast(resources.getString(R.string.kGPErrorAccountNotExist))
                    return
                }
                if (msg.indexOf("Insufficient Balance") > 0) {
                    showToast(resources.getString(R.string.kGPErrorInsufficientBalance))
                    return
                }
            }
        } catch (e: Exception) {
        }
    }
    //  默认错误信息
    showToast(resources.getString(R.string.nameNetworkException))
}

fun Fragment.showGrapheneError(error: Any?) {
    this.activity?.showGrapheneError(error)
}

/**
 * (public) 辅助 - 显示水龙头的时的错误信息，根据 code 进行错误显示便于处理多语言。
 */
fun android.app.Activity.showFaucetRegisterError(response: JSONObject?) {
    if (response != null) {
        val code = response.getInt("status")
        if (code != 0) {
            when (code) {
                10 -> showToast(resources.getString(R.string.registerLoginPageInvalidParams))
                20 -> showToast(resources.getString(R.string.registerLoginPageInvalidAccountFormat))
                30 -> showToast(resources.getString(R.string.registerLoginPageAccountExisted))
                40 -> showToast(resources.getString(R.string.registerLoginPageUnknownErrorAndBroadcastFailed))
                41 -> showToast(resources.getString(R.string.registerLoginPageTooManyAccountForThisEquipment))
                42 -> showToast(resources.getString(R.string.registerLoginPageRegisterFrequently))
                999 -> showToast(resources.getString(R.string.registerLoginPageServerMaintenance))
                else -> showToast(response.getString("msg"))
            }
        }
    } else {
        showToast(resources.getString(R.string.nameNetworkException))
    }
}


/**
 *  (private) 创建提案请求
 */
fun android.app.Activity.onExecuteCreateProposalCore(opcode: EBitsharesOperations, opdata: JSONObject, opaccount: JSONObject, fee_paying_account: JSONObject, success_callback: (() -> Unit)?) {
    val fee_paying_account_id = fee_paying_account.getString("id")
    //  请求
    val mask = ViewMesk(R.string.nameRequesting.xmlstring(this), this)
    mask.show()
    BitsharesClientManager.sharedBitsharesClientManager().proposalCreate(opcode, opdata, opaccount, fee_paying_account_id).then {
        mask.dismiss()
        if (success_callback != null) {
            success_callback()
        } else {
            showToast(R.string.kProposalSubmitTipTxOK.xmlstring(this))
        }
        fabricLogCustom("txProposalCreateOK", jsonObjectfromKVS("opcode", opcode.value, "account", fee_paying_account_id))
        return@then null
    }.catch { err ->
        mask.dismiss()
        showGrapheneError(err)
        fabricLogCustom("txProposalCreateFailed", jsonObjectfromKVS("opcode", opcode.value, "account", fee_paying_account_id))
    }
}

/**
 *  (public)权限不足时，询问用户是否发起提案交易。
 */
fun android.app.Activity.askForCreateProposal(opcode: EBitsharesOperations, using_owner_authority: Boolean, invoke_proposal_callback: Boolean,
                                              opdata: JSONObject, opaccount: JSONObject,
                                              body: ((isProposal: Boolean, fee_paying_account: JSONObject) -> Unit)?, success_callback: (() -> Unit)?) {
    val account_name = opaccount.getString("name")
    var message: String
    if (using_owner_authority) {
        message = String.format(R.string.kProposalTipsAskMissingOwner.xmlstring(this), account_name)
    } else {
        message = String.format(R.string.kProposalTipsAskMissingActive.xmlstring(this), account_name)
    }
    alerShowMessageConfirm(resources.getString(R.string.registerLoginPageWarmTip), message).then {
        if (it != null && it as Boolean) {
            //  转到提案确认界面
            val result_promise = Promise()
            val args = jsonObjectfromKVS("opcode", opcode, "opdata", opdata, "result_promise", result_promise)
            goTo(ActivityCreateProposal::class.java, true, args = args)
            result_promise.then { result ->
                val selected_fee_paying_account = result as? JSONObject
                if (selected_fee_paying_account != null) {
                    if (invoke_proposal_callback) {
                        body!!(true, selected_fee_paying_account)
                    } else {
                        onExecuteCreateProposalCore(opcode, opdata, opaccount, selected_fee_paying_account, success_callback)
                    }
                }
            }
        }
        return@then null
    }
}

/**
 *  (public) 确保交易权限。足够-发起普通交易，不足-提醒用户发起提案交易。
 *  using_owner_authority - 是否使用owner授权，否则验证active权限。
 */
fun android.app.Activity.GuardProposalOrNormalTransaction(opcode: EBitsharesOperations, using_owner_authority: Boolean, invoke_proposal_callback: Boolean,
                                                          opdata: JSONObject, opaccount: JSONObject,
                                                          body: (isProposal: Boolean, fee_paying_account: JSONObject?) -> Unit) {
    val permission_json = if (using_owner_authority) opaccount.getJSONObject("owner") else opaccount.getJSONObject("active")
    if (WalletManager.sharedWalletManager().canAuthorizeThePermission(permission_json)) {
        //  权限足够
        body(false, null)
    } else {
        //  没权限，询问用户是否发起提案。
        askForCreateProposal(opcode, using_owner_authority, invoke_proposal_callback, opdata, opaccount, body, null)
    }
}

/**
 * 确保钱包已经解锁、检测是否包含资金私钥权限。
 */
fun android.app.Activity.guardWalletUnlocked(checkActivePermission: Boolean, body: (unlocked: Boolean) -> Unit) {
    val walletMgr = WalletManager.sharedWalletManager()
    if (walletMgr.isLocked()) {
        var title = if (walletMgr.getWalletMode() == AppCacheManager.EWalletMode.kwmPasswordOnlyMode.value) R.string.unlockTipsUnlockAccount.xmlstring(this) else R.string.unlockTipsUnlockWallet.xmlstring(this)
        val placeholder = when (walletMgr.getWalletMode()) {
            AppCacheManager.EWalletMode.kwmPasswordOnlyMode.value -> resources.getString(R.string.registerLoginPagePleaseInputAccountPwd)
            AppCacheManager.EWalletMode.kwmPasswordWithWallet.value -> resources.getString(R.string.registerLoginPagePleaseInputTradePws)
            AppCacheManager.EWalletMode.kwmPrivateKeyWithWallet.value -> resources.getString(R.string.registerLoginPagePleaseInputTradePws)
            AppCacheManager.EWalletMode.kwmFullWalletMode.value -> resources.getString(R.string.registerLoginPagePleaseInputWalletPws)
            else -> resources.getString(R.string.registerLoginPagePleaseInputPwd)
        }
        UtilsAlert.showInputBox(this, title, placeholder, resources.getString(R.string.unlockBtnUnlock)).then {
            val password = it as? String
            if (password == null) {
                body(false)
            } else if (password == "") {
                showToast(resources.getString(R.string.registerLoginPagePasswordIsEmpty))
            } else {
                val unlockInfos = WalletManager.sharedWalletManager().unLock(password, this)
                var unlockSuccess = unlockInfos.getBoolean("unlockSuccess")
                if (unlockSuccess && checkActivePermission && !unlockInfos.optBoolean("haveActivePermission")) {
                    unlockSuccess = false
                }
                if (unlockSuccess) {
                    body(true)
                } else {
                    showToast(unlockInfos.getString("err"))
                    body(false)
                }
            }
        }
    } else {
        body(true)
    }
}

/**
 * 确保钱包已经解锁（否则会转到解锁处理）REMARK：首先会确保钱包已经存在，并且需要有资金权限。
 */
fun android.app.Activity.guardWalletUnlocked(body: (unlocked: Boolean) -> Unit) {
    guardWalletUnlocked(true, body)
}

/**
 * 确保钱包存在（否则会转到导入帐号处理）
 */
fun android.app.Activity.guardWalletExist(body: () -> Unit) {
    if (WalletManager.sharedWalletManager().isWalletExist()) {
        body()
    } else {
        goTo(ActivityLogin::class.java, true)
    }
}

/**
 * 返回桌面
 */
fun AppCompatActivity.goHome() {
    val home = Intent(Intent.ACTION_MAIN)
    home.addCategory(Intent.CATEGORY_HOME)
    startActivity(home)
}

/**
 * 转到webview界面
 */
fun android.app.Activity.goToWebView(title: String, url: String) {
    goTo(ActivityWebView::class.java, true, args = arrayOf(title, url))
}

/**
 * 用系统浏览器打开页面。
 */
fun android.app.Activity.openURL(url: String) {
    try {
        val uri = Uri.parse(url)
        val intent = Intent(Intent.ACTION_VIEW, uri)
        startActivity(intent)
    } catch (e: Exception) {
        //  TODO:无效URL等异常
    }
}

fun android.app.Activity.goTo(cls: Class<*>, transition_animation: Boolean = false, back: Boolean = false, args: Any? = null, request_code: Int = -1) {
    val intent = Intent()
    intent.setClass(this, cls)

    if (back) {
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    //  设置参数
    TempManager.sharedTempManager().set_args(args)

    //  是否获取结果
    if (request_code > 0) {
        startActivityForResult(intent, request_code)
    } else {
        startActivity(intent)
    }

    if (!transition_animation) {
        overridePendingTransition(0, 0)
    }
}

/**
 * 是否存在虚拟导航栏判断。
 */
fun android.app.Activity.isHaveNavigationBar(): Boolean {
    val display = this.windowManager.defaultDisplay
    val size = Point()
    val realsize = Point()
    //  可显示大小
    display.getSize(size)
    //  包含虚拟导航栏大小
    display.getRealSize(realsize)
    return size.y != realsize.y
}

/**
 * 设置自动调整高度的 contentView
 */
fun AppCompatActivity.setAutoLayoutContentView(layoutResID: Int, navigationBarColor: Int? = null) {
    setContentView(layoutResID)
    adjustWindowSizeForNavigationBar(navigationBarColor)
    //  [统计]
    fabricLogCustom("setAutoLayoutContentView", jsonObjectfromKVS("activity", this::class.java.name))
}

/**
 * 适配虚拟机导航栏
 */
fun android.app.Activity.adjustWindowSizeForNavigationBar(navigationBarColor: Int? = null) {
    val display = this.windowManager.defaultDisplay
    val size = Point()
    val realsize = Point()
    display.getSize(size)
    display.getRealSize(realsize)
    if (size.y != realsize.y) {
        val contentView = findViewById<View>(android.R.id.content)
        //  更改布局高度（留出虚拟导航栏位置）
        contentView.layoutParams.height = size.y
        //  设置留出的导航栏区域背景
        if (navigationBarColor != null) {
            contentView.rootView?.setBackgroundColor(resources.getColor(navigationBarColor))
        } else {
            contentView.rootView?.setBackgroundColor(resources.getColor(R.color.theme01_appBackColor))
        }
    }
}

fun AppCompatActivity.toDp(v: Float): Int {
    return Utils.toDp(v, this.resources)
}

class ViewPagerAdapter(fm: FragmentManager, _fragmets: ArrayList<Fragment>) : FragmentPagerAdapter(fm) {

    val fragments: ArrayList<Fragment> = _fragmets

    override fun getItem(p0: Int): Fragment {
        return fragments[p0]
    }

    override fun getCount(): Int {
        return fragments.size
    }
}

class ViewPagerScroller(context: Context?, interpolator: OvershootInterpolator) : Scroller(context) {

    var mDuration: Int = 0

    fun setDuration(_mDuration: Int) {
        mDuration = _mDuration
    }

    override fun startScroll(startX: Int, startY: Int, dx: Int, dy: Int) {
        super.startScroll(startX, startY, dx, dy, this.mDuration)
    }

    override fun startScroll(startX: Int, startY: Int, dx: Int, dy: Int, duration: Int) {
        super.startScroll(startX, startY, dx, dy, this.mDuration)
    }
}

fun AppCompatActivity.setViewPager(default_select_index: Int, view_pager_id: Int, tablayout_id: Int, fragmens: ArrayList<Fragment>) {
    val _view_pager = findViewById<ViewPager>(view_pager_id)
    _view_pager.adapter = ViewPagerAdapter(supportFragmentManager, fragmens)

    val f: Field = ViewPager::class.java.getDeclaredField("mScroller")
    f.isAccessible = true
    val vpc = ViewPagerScroller(_view_pager.context, OvershootInterpolator(0.6f))
    f.set(_view_pager, vpc)
    vpc.duration = 700

    //  default selected
    val _tablayout = findViewById<TabLayout>(tablayout_id)
    _tablayout.getTabAt(default_select_index)!!.select()
    _view_pager.currentItem = default_select_index

    _view_pager.setOnPageChangeListener(object : ViewPager.OnPageChangeListener {
        override fun onPageScrollStateChanged(state: Int) {
        }

        override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
        }

        override fun onPageSelected(position: Int) {
            _tablayout.getTabAt(position)!!.select()
        }
    })
}

fun AppCompatActivity.setTabListener(tablayout_id: Int, view_pager_id: Int) {
    val _view_pager = findViewById<ViewPager>(view_pager_id)
    findViewById<TabLayout>(tablayout_id).setOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
        override fun onTabSelected(tab: TabLayout.Tab) {
            _view_pager.setCurrentItem(tab.position, true)
        }

        override fun onTabUnselected(tab: TabLayout.Tab) {
            //tab未被选择的时候回调
        }

        override fun onTabReselected(tab: TabLayout.Tab) {
            //tab重新选择的时候回调
        }
    })
}

