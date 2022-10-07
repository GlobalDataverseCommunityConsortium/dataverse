package edu.harvard.iq.dataverse.pidproviders;

import java.util.Arrays;
import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.ejb.Singleton;
import javax.ejb.Startup;

import edu.harvard.iq.dataverse.DOIDataCiteServiceBean;
import edu.harvard.iq.dataverse.DOIEZIdServiceBean;
import edu.harvard.iq.dataverse.HandlenetServiceBean;

    /**
     * This is a small helper bean 
     * As it is a singleton and built at application start (=deployment), it will inject the (stateless)
     * dataverse service into the BrandingUtil once it's ready.
     */
    @Startup
    @Singleton
    public class PIDHelper {

        @EJB
        DOIDataCiteServiceBean datacitePidSvc;
        @EJB
        DOIEZIdServiceBean ezidPidSvc;
        @EJB
        HandlenetServiceBean handlePidSvc;
        @EJB
        FakePidProviderServiceBean fakePidSvc;
        @EJB
        PermaLinkPidProviderServiceBean permaPidSvc;

        @PostConstruct
        public void listServices() {
            PidUtil.addAllToProviderList(Arrays.asList(datacitePidSvc, ezidPidSvc, handlePidSvc, permaPidSvc, fakePidSvc));
        }

    }