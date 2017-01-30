//jDownloader - Downloadmanager
//Copyright (C) 2009  JD-Team support@jdownloader.org
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

package jd.plugins.decrypter;

import java.util.ArrayList;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.parser.html.Form;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterException;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginForDecrypt;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "links.snahp.it" }, urls = { "https?://(?:www\\.)?links\\.snahp\\.it/[A-Za-z0-9\\-_]+" })
public class LinksSnahpIt extends PluginForDecrypt {

    public LinksSnahpIt(PluginWrapper wrapper) {
        super(wrapper);
    }

    /* Tags: Daddy's Link Protector */

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final String parameter = param.toString();
        br.getPage(parameter);
        if (br.getHttpConnection().getResponseCode() == 404 || this.br.containsHTML("class=\"error\"")) {
            decryptedLinks.add(this.createOfflinelink(parameter));
            return decryptedLinks;
        }
        boolean captchaFail = false;
        boolean passwordFail = false;
        int counter = 0;
        Form confirmationForm = null;
        do {
            confirmationForm = this.br.getForm(0);
            if (confirmationForm == null) {
                passwordFail = false;
                captchaFail = false;
                break;
            }
            /* 2017-01-30: Either captcha OR password */
            if (confirmationForm.hasInputFieldByName("security_code")) {
                captchaFail = true;
                final String code = this.getCaptchaCode("/CaptchaSecurityImages.php?width=100&height=40&characters=5", param);
                confirmationForm.put("security_code", Encoding.urlEncode(code));
            } else if (confirmationForm.hasInputFieldByName("Pass1")) {
                passwordFail = true;
                confirmationForm.put("Pass1", Encoding.urlEncode(getUserInput("Password?", param)));
            } else {
                passwordFail = false;
                captchaFail = false;
                break;
            }
            this.br.submitForm(confirmationForm);
            counter++;
        } while (confirmationForm != null && counter <= 2);
        if (captchaFail) {
            throw new DecrypterException(DecrypterException.CAPTCHA);
        } else if (passwordFail) {
            throw new DecrypterException(DecrypterException.PASSWORD);
        }
        String fpName = new Regex(parameter, "/([^/]+)$").getMatch(0);
        final String[] links = br.getRegex("<p><a href=\"(http[^<>\"]+)\"").getColumn(0);
        if (links == null || links.length == 0) {
            logger.warning("Decrypter broken for link: " + parameter);
            return null;
        }
        for (final String singleLink : links) {
            if (singleLink.contains(this.getHost())) {
                continue;
            }
            decryptedLinks.add(createDownloadlink(singleLink));
        }

        if (fpName != null) {
            final FilePackage fp = FilePackage.getInstance();
            fp.setName(Encoding.htmlDecode(fpName.trim()));
            fp.addLinks(decryptedLinks);
        }

        return decryptedLinks;
    }

}