//jDownloader - Downloadmanager
//Copyright (C) 2013  JD-Team support@jdownloader.org
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

import java.util.ArrayList;
import java.util.List;

import org.jdownloader.plugins.components.XFileSharingProBasic;

import jd.PluginWrapper;
import jd.plugins.Account;
import jd.plugins.Account.AccountType;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = {}, urls = {})
public class DbuploadIn extends XFileSharingProBasic {
    public DbuploadIn(final PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium(super.getPurchasePremiumURL());
    }

    /**
     * DEV NOTES XfileSharingProBasic Version SEE SUPER-CLASS<br />
     * mods: See overridden functions<br />
     * limit-info: 2019-05-03: untested as signup-page was broken during testing, set FREE limits <br />
     * captchatype-info: 2019-05-03: null<br />
     * other:<br />
     */
    public static List<String[]> getPluginDomains() {
        final List<String[]> ret = new ArrayList<String[]>();
        // each entry in List<String[]> will result in one PluginForHost, Plugin.getHost() will return String[0]->main domain
        ret.add(new String[] { "dbupload.co", "dbupload.in" });
        return ret;
    }

    public static String[] getAnnotationNames() {
        return buildAnnotationNames(getPluginDomains());
    }

    @Override
    public String[] siteSupportedNames() {
        return buildSupportedNames(getPluginDomains());
    }

    public static String[] getAnnotationUrls() {
        return XFileSharingProBasic.buildAnnotationUrls(getPluginDomains());
    }

    @Override
    public String rewriteHost(String host) {
        /* 2019-10-05: Main domain has changed from dbupload.in to dbupload.co */
        return this.rewriteHost(getPluginDomains(), host, new String[0]);
    }

    @Override
    public boolean isResumeable(final DownloadLink link, final Account account) {
        if (account != null && account.getType() == AccountType.FREE) {
            /* Free Account */
            return true;
        } else if (account != null && account.getType() == AccountType.PREMIUM) {
            /* Premium account */
            return true;
        } else {
            /* Free(anonymous) and unknown account type */
            return true;
        }
    }

    @Override
    public int getMaxChunks(final Account account) {
        if (account != null && account.getType() == AccountType.FREE) {
            /* Free Account */
            return -5;
        } else if (account != null && account.getType() == AccountType.PREMIUM) {
            /* Premium account */
            return -5;
        } else {
            /* Free(anonymous) and unknown account type */
            return -5;
        }
    }

    @Override
    public int getMaxSimultaneousFreeAnonymousDownloads() {
        return 2;
    }

    @Override
    public int getMaxSimultaneousFreeAccountDownloads() {
        return 2;
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        return 2;
    }

    @Override
    public AvailableStatus requestFileInformationWebsite(final DownloadLink link, final Account account, final boolean downloadsStarted) throws Exception {
        /* 2019-10-05: Special: Workaround for serverside bad filenames */
        final AvailableStatus status = super.requestFileInformationWebsite(link, account, downloadsStarted);
        final String filename = link.getName();
        if (filename != null && this.fuid != null && !filename.equals(this.fuid)) {
            link.setFinalFileName(filename);
        }
        return status;
    }

    @Override
    protected void fixFilename(final DownloadLink downloadLink) {
        /* 2019-10-05: Special: Workaround for serverside bad filenames */
    }
}