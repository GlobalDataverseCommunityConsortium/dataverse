package edu.harvard.iq.dataverse.branding;

import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import edu.harvard.iq.dataverse.util.BundleUtil;
import java.util.Arrays;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.mail.internet.InternetAddress;

@Stateless
public class BrandingUtil {

    @Inject
    SettingsServiceBean settings;
    
    public String getInstallationBrandName(String rootDataverseName) {
        return rootDataverseName;
    }

    public String getSupportTeamName(InternetAddress systemAddress, String rootDataverseName) {
        if (systemAddress != null) {
            String personalName = systemAddress.getPersonal();
            if (personalName != null) {
                return personalName;
            }
        }
        if (rootDataverseName != null && !rootDataverseName.isEmpty()) {
            return rootDataverseName + " " + BundleUtil.getStringFromBundle("contact.support");
        }
        String saneDefault = BundleUtil.getStringFromBundle("dataverse");
        return BundleUtil.getStringFromBundle("contact.support", Arrays.asList(saneDefault));
    }

    public String getSupportTeamEmailAddress(InternetAddress systemAddress) {
        if (systemAddress == null) {
            return null;
        }
        return systemAddress.getAddress();
    }

    public String getContactHeader(InternetAddress systemAddress, String rootDataverseName) {
        return BundleUtil.getStringFromBundle("contact.header", Arrays.asList(getSupportTeamName(systemAddress, rootDataverseName)));
    }
    
    public String getInstitutionName() {
        return settings.get(":InstitutionName", "Not Set");
    }

}
