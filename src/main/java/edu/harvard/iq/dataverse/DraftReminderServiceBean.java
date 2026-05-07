package edu.harvard.iq.dataverse;

import edu.harvard.iq.dataverse.authorization.Permission;
import edu.harvard.iq.dataverse.authorization.users.AuthenticatedUser;
import edu.harvard.iq.dataverse.settings.FeatureFlags;
import edu.harvard.iq.dataverse.util.SystemConfig;
import jakarta.ejb.EJB;
import jakarta.ejb.Schedule;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.sql.Timestamp;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.logging.Logger;

@Stateless
public class DraftReminderServiceBean {

    private static final Logger logger = Logger.getLogger(DraftReminderServiceBean.class.getCanonicalName());

    @PersistenceContext(unitName = "VDCNet-ejbPU")
    private EntityManager em;

    @EJB
    UserNotificationServiceBean userNotificationService;

    @EJB
    PermissionServiceBean permissionService;

    @EJB
    SystemConfig systemConfig;

    @Schedule(hour = "0", minute = "0", persistent = false)
    public void sendDraftReminders() {
        if (!FeatureFlags.NOTIFY_ON_UNPUBLISHED_DRAFTS.enabled()) {
            return;
        }

        if (!systemConfig.isTimerServer()) {
            return;
        }

        logger.info("Starting DraftReminderServiceBean.sendDraftReminders");

        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime sixMonthsAgo = now.minusMonths(6);
        ZonedDateTime sixMonthsAndADayAgo = sixMonthsAgo.minusDays(1);

        Timestamp start = Timestamp.from(sixMonthsAndADayAgo.toInstant());
        Timestamp end = Timestamp.from(sixMonthsAgo.toInstant());

        // Query for DatasetVersion with state DRAFT and lastUpdateTime in range
        TypedQuery<DatasetVersion> query = em.createQuery(
                "SELECT v FROM DatasetVersion v WHERE v.versionState = :state AND v.lastUpdateTime >= :start AND v.lastUpdateTime < :end",
                DatasetVersion.class);
        query.setParameter("state", DatasetVersion.VersionState.DRAFT);
        query.setParameter("start", start);
        query.setParameter("end", end);

        List<DatasetVersion> draftVersions = query.getResultList();
        logger.info("Found " + draftVersions.size() + " draft versions to notify about.");

        for (DatasetVersion dv : draftVersions) {
            Dataset dataset = dv.getDataset();
            List<AuthenticatedUser> editors = permissionService.getUsersWithPermissionOn(Permission.EditDataset, dataset);
            
            for (AuthenticatedUser editor : editors) {
                userNotificationService.sendNotification(editor, Timestamp.from(now.toInstant()), UserNotification.Type.UNPUBLISHED_DRAFTS_REMINDER, dv.getId());
            }
        }
        
        logger.info("Finished DraftReminderServiceBean.sendDraftReminders");
    }
}
