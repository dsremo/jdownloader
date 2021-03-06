//jDownloader - Downloadmanager
//Copyright (C) 2011  JD-Team support@jdownloader.org
//
//This program is free software: you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation, either version 3 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program.  If not, see <http://www.gnu.org/licenses/>.
package jd.plugins.hoster;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.appwork.storage.simplejson.JSonArray;
import org.appwork.storage.simplejson.JSonFactory;
import org.appwork.storage.simplejson.JSonNode;
import org.appwork.storage.simplejson.JSonObject;
import org.appwork.utils.StringUtils;
import org.appwork.utils.formatter.TimeFormatter;
import org.jdownloader.gui.IconKey;
import org.jdownloader.gui.views.downloads.columns.ETAColumn;
import org.jdownloader.images.AbstractIcon;
import org.jdownloader.plugins.PluginTaskID;
import org.jdownloader.plugins.controller.host.LazyHostPlugin.FEATURE;

import jd.PluginWrapper;
import jd.config.Property;
import jd.http.Browser;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.plugins.Account;
import jd.plugins.Account.AccountType;
import jd.plugins.AccountInfo;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.plugins.PluginProgress;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "premium.rpnet.biz" }, urls = { "https?://(www\\.)?dl[^\\.]*.rpnet\\.biz/download/.*/([^/\\s]+)?" })
public class RPNetBiz extends PluginForHost {
    private static final String mName              = "rpnet.biz";
    private static final String mProt              = "http://";
    private static final String api_base           = "https://premium.rpnet.biz/";
    private static final int    HDD_WAIT_THRESHOLD = 10 * 60000;                  // 10 mins in

    // ms
    public RPNetBiz(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium(mProt + mName + "/");
    }

    @Override
    public void correctDownloadLink(final DownloadLink link) throws Exception {
        link.setUrlDownload(link.getDownloadURL().replace("http://www.", "http://"));
    }

    @Override
    public String rewriteHost(String host) {
        if (host == null || "rpnet.biz".equals(host)) {
            return "premium.rpnet.biz";
        }
        return super.rewriteHost(host);
    }

    @Override
    public FEATURE[] getFeatures() {
        return new FEATURE[] { FEATURE.MULTIHOST };
    }

    @Override
    public String getAGBLink() {
        return api_base + "tos.php";
    }

    public void prepBrowser() {
        // define custom browser headers and language settings.
        br.getHeaders().put("Accept-Language", "en-gb, en;q=0.9");
        br.getHeaders().put("User-Agent", "JDownloader");
        br.setCustomCharset("utf-8");
        br.setConnectTimeout(60 * 1000);
        br.setReadTimeout(60 * 1000);
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        // tested with 20 seems fine.
        return -1;
    }

    @Override
    public void handlePremium(final DownloadLink link, final Account account) throws Exception, PluginException {
        handleDL(link, link.getPluginPatternMatcher(), -6);
    }

    @Override
    public void handleFree(DownloadLink link) throws Exception, PluginException {
        // requestFileInformation(downloadLink);
        dl = jd.plugins.BrowserAdapter.openDownload(br, link, link.getPluginPatternMatcher(), true, -6);
        URLConnectionAdapter con = dl.getConnection();
        List<Integer> allowedResponseCodes = Arrays.asList(200, 206);
        if (!allowedResponseCodes.contains(con.getResponseCode()) || con.getContentType().contains("html") || con.getResponseMessage().contains("Download doesn't exist for given Hash/ID/Key")) {
            try {
                br.followConnection();
            } catch (final Throwable e) {
            }
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        dl.startDownload();
    }

    @Override
    public AvailableStatus requestFileInformation(DownloadLink link) throws IOException, PluginException {
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        prepBrowser();
        URLConnectionAdapter con = null;
        try {
            con = br.openGetConnection(link.getDownloadURL());
            List<Integer> allowedResponseCodes = Arrays.asList(200, 206);
            if (!allowedResponseCodes.contains(con.getResponseCode()) || con.getContentType().contains("html") || con.getResponseMessage().contains("Download doesn't exist for given Hash/ID/Key")) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            link.setName(getFileNameFromHeader(con));
            link.setDownloadSize(con.getLongContentLength());
        } finally {
            try {
                con.disconnect();
            } catch (final Throwable e) {
            }
        }
        return AvailableStatus.TRUE;
    }

    @Override
    public AccountInfo fetchAccountInfo(Account account) throws Exception {
        if (StringUtils.isEmpty(account.getUser())) {
            throw new PluginException(LinkStatus.ERROR_PREMIUM, "User name can not be empty!", PluginException.VALUE_ID_PREMIUM_DISABLE);
        } else if (StringUtils.isEmpty(account.getPass()) || !account.getPass().matches("[a-f0-9]{40}")) {
            throw new PluginException(LinkStatus.ERROR_PREMIUM, "You need to use API Key as password!\r\nYou can find it here: premium.rpnet.biz/usercp.php?action=showAccountInfo", PluginException.VALUE_ID_PREMIUM_DISABLE);
        }
        AccountInfo ai = new AccountInfo();
        prepBrowser();
        br.getPage(api_base + "client_api.php?username=" + Encoding.urlEncode(account.getUser()) + "&password=" + URLEncoder.encode(account.getPass(), "UTF-8") + "&action=showAccountInformation");
        if (br.toString().contains("Invalid authentication.")) {
            throw new PluginException(LinkStatus.ERROR_PREMIUM, "Invalid User : API Key", PluginException.VALUE_ID_PREMIUM_DISABLE);
        } else if (br.containsHTML("IP Ban in effect for")) {
            throw new PluginException(LinkStatus.ERROR_PREMIUM, "Your account is temporarily banned", PluginException.VALUE_ID_PREMIUM_TEMP_DISABLE);
        }
        JSonObject node = (JSonObject) new JSonFactory(br.toString()).parse();
        JSonObject accountInfo = (JSonObject) node.get("accountInfo");
        long expiryDate = Long.parseLong(accountInfo.get("premiumExpiry").toString().replaceAll("\"", ""));
        ai.setValidUntil(expiryDate * 1000, br);
        String hosts = br.getPage(api_base + "hostlist.php");
        if (hosts != null) {
            ArrayList<String> supportedHosts = new ArrayList<String>(Arrays.asList(hosts.split(",")));
            ai.setMultiHostSupport(this, supportedHosts);
        }
        account.setType(AccountType.PREMIUM);
        ai.setStatus("Premium Account");
        return ai;
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

    @Override
    public boolean canHandle(DownloadLink downloadLink, Account account) throws Exception {
        if (account == null) {
            /* without account its not possible to download the link */
            return false;
        }
        return true;
    }

    /** no override to keep plugin compatible to old stable */
    public void handleMultiHost(final DownloadLink link, final Account account) throws Exception {
        final String downloadURL = link.getDefaultPlugin().buildExternalDownloadURL(link, this);
        prepBrowser();
        String generatedLink = checkDirectLink(link, "cachedDllink");
        int maxChunks = 0;
        String filename = null;
        if (generatedLink != null) {
            logger.info("Reusing cached download link");
        } else {
            // end of workaround
            showMessage(link, "Generating Link");
            /* request Download */
            String apiDownloadLink = api_base + "client_api.php?username=" + Encoding.urlEncode(account.getUser()) + "&password=" + Encoding.urlEncode(account.getPass()) + "&action=generate&links=" + Encoding.urlEncode(downloadURL);
            br.getPage(apiDownloadLink);
            JSonObject node = (JSonObject) new JSonFactory(br.toString().replaceAll("\\\\/", "/")).parse();
            JSonArray links = (JSonArray) node.get("links");
            // for now there is only one generated link per api call, could be changed in the future, therefore iterate anyway
            for (JSonNode linkNode : links) {
                JSonObject linkObj = (JSonObject) linkNode;
                JSonNode errorNode = linkObj.get("error");
                if (errorNode != null) {
                    // shows a more detailed error message returned by the API, especially if the DL Limit is reached for a host
                    String msg = errorNode.toString();
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, msg);
                }
                // Only ID given? => request the download from rpnet hdd
                JSonNode idNode = linkObj.get("id");
                generatedLink = null;
                if (idNode != null) {
                    final String id = idNode.toString();
                    final PluginProgress waitProgress = new PluginProgress(0, 100, null) {
                        protected long lastCurrent    = -1;
                        protected long lastTotal      = -1;
                        protected long startTimeStamp = -1;

                        @Override
                        public PluginTaskID getID() {
                            return PluginTaskID.WAIT;
                        }

                        @Override
                        public String getMessage(Object requestor) {
                            if (requestor instanceof ETAColumn) {
                                final long eta = getETA();
                                if (eta >= 0) {
                                    return TimeFormatter.formatMilliSeconds(eta, 0);
                                }
                                return "";
                            }
                            return "Waiting for upload to rpnet HDD";
                        }

                        @Override
                        public void updateValues(long current, long total) {
                            super.updateValues(current, total);
                            if (startTimeStamp == -1 || lastTotal == -1 || lastTotal != total || lastCurrent == -1 || lastCurrent > current) {
                                lastTotal = total;
                                lastCurrent = current;
                                startTimeStamp = System.currentTimeMillis();
                                // this.setETA(-1);
                                return;
                            }
                            long currentTimeDifference = System.currentTimeMillis() - startTimeStamp;
                            if (currentTimeDifference <= 0) {
                                return;
                            }
                            long speed = (current * 10000) / currentTimeDifference;
                            if (speed == 0) {
                                return;
                            }
                            long eta = ((total - current) * 10000) / speed;
                            this.setETA(eta);
                        }
                    };
                    waitProgress.setIcon(new AbstractIcon(IconKey.ICON_WAIT, 16));
                    waitProgress.setProgressSource(this);
                    try {
                        long lastProgressChange = System.currentTimeMillis();
                        int lastProgress = -1;
                        while (System.currentTimeMillis() - lastProgressChange < HDD_WAIT_THRESHOLD) {
                            if (isAbort()) {
                                logger.info("Process aborted by user");
                                throw new PluginException(LinkStatus.ERROR_RETRY);
                            }
                            br.getPage(api_base + "client_api.php?username=" + Encoding.urlEncode(account.getUser()) + "&password=" + Encoding.urlEncode(account.getPass()) + "&action=downloadInformation&id=" + Encoding.urlEncode(id));
                            final JSonObject node2 = (JSonObject) new JSonFactory(br.toString().replaceAll("\\\\/", "/")).parse();
                            final JSonObject downloadNode = (JSonObject) node2.get("download");
                            final String tmp = downloadNode.get("status").toString();
                            final Integer currentProgress = Integer.parseInt(tmp.substring(1, tmp.length() - 1));
                            // download complete?
                            if (currentProgress.intValue() == 100) {
                                String tmp2 = downloadNode.get("rpnet_link").toString();
                                final Object max_connections = downloadNode.get("max_connections");
                                if (max_connections != null) {
                                    final int chunks = Integer.valueOf(max_connections.toString());
                                    if (chunks > 0) {
                                        maxChunks = -chunks;
                                    }
                                }
                                generatedLink = tmp2.substring(1, tmp2.length() - 1);
                                break;
                            } else {
                                link.addPluginProgress(waitProgress);
                                waitProgress.updateValues(currentProgress.intValue(), 100);
                                for (int sleepRound = 0; sleepRound < 10; sleepRound++) {
                                    if (isAbort()) {
                                        throw new PluginException(LinkStatus.ERROR_RETRY);
                                    } else {
                                        Thread.sleep(1000);
                                    }
                                }
                                if (currentProgress.intValue() != lastProgress) {
                                    lastProgressChange = System.currentTimeMillis();
                                    lastProgress = currentProgress.intValue();
                                }
                            }
                        }
                    } finally {
                        link.removePluginProgress(waitProgress);
                    }
                } else {
                    /* Direct download */
                    String tmp = ((JSonObject) linkNode).get("generated").toString();
                    final Object max_connections = ((JSonObject) linkNode).get("max_connections");
                    if (max_connections != null) {
                        final int chunks = Integer.valueOf(max_connections.toString());
                        if (chunks > 0) {
                            maxChunks = -chunks;
                        }
                    }
                    generatedLink = tmp.substring(1, tmp.length() - 1);
                    filename = ((JSonObject) linkNode).get("filename").toString();
                    break;
                }
            }
        }
        showMessage(link, "Download begins!");
        if (StringUtils.isEmpty(generatedLink)) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Failed to find final downloadurl", 3 * 60 * 1000l);
        }
        try {
            /*
             * 2020-04-27: According to admin, should use this for zippyshare ONLY.
             */
            if (!StringUtils.isEmpty(filename) && link.getHost().equalsIgnoreCase("zippyshare.com")) {
                /* 2020-04-24: E.g. sometimes "Testfile.rar" (With "" --> WTF, remove that) */
                filename = filename.replace("\"", "");
                logger.info("Using final filename given by API: " + filename);
                link.setFinalFileName(filename);
            } else {
                logger.info("Using final filename from Content-Disposition Header");
            }
            handleDL(link, generatedLink, maxChunks);
            return;
        } catch (final PluginException e1) {
            if (e1.getLinkStatus() == LinkStatus.ERROR_DOWNLOAD_INCOMPLETE) {
                logger.info("rpnet.biz: ERROR_DOWNLOAD_INCOMPLETE --> Quitting loop");
                throw e1;
            } else if (e1.getLinkStatus() == LinkStatus.ERROR_DOWNLOAD_FAILED) {
                logger.info("rpnet.biz: ERROR_DOWNLOAD_FAILED --> Quitting loop");
                throw e1;
            }
        }
    }

    private void handleDL(final DownloadLink link, String dllink, int maxChunks) throws Exception {
        /* we want to follow redirects in final stage */
        dl = jd.plugins.BrowserAdapter.openDownload(br, link, dllink, true, maxChunks);
        if (dl.getConnection().isContentDisposition()) {
            link.setProperty("cachedDllink", dllink);
            /* contentdisposition, lets download it */
            dl.startDownload();
            return;
        } else {
            /*
             * download is not contentdisposition, so remove this host from premiumHosts list
             */
            br.followConnection();
        }
        /* temp disabled the host */
        throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Unknown download error");
    }

    private void showMessage(DownloadLink link, String message) {
        link.getLinkStatus().setStatusText(message);
    }

    private String checkDirectLink(final DownloadLink downloadLink, final String property) {
        String dllink = downloadLink.getStringProperty(property, null);
        if (dllink != null) {
            URLConnectionAdapter con = null;
            try {
                Browser br2 = br.cloneBrowser();
                br2.setFollowRedirects(true);
                con = br2.openGetConnection(dllink);
                List<Integer> allowedResponseCodes = Arrays.asList(200, 206);
                if (!allowedResponseCodes.contains(con.getResponseCode()) || con.getContentType().contains("html") || con.getLongContentLength() == -1 || con.getResponseMessage().contains("Download doesn't exist for given Hash/ID/Key")) {
                    downloadLink.setProperty(property, Property.NULL);
                    dllink = null;
                }
            } catch (final Exception e) {
                downloadLink.setProperty(property, Property.NULL);
                dllink = null;
            } finally {
                try {
                    con.disconnect();
                } catch (final Throwable e) {
                }
            }
        }
        return dllink;
    }
}