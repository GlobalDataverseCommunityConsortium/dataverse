package edu.harvard.iq.dataverse;

import edu.harvard.iq.dataverse.authorization.Permission;
import edu.harvard.iq.dataverse.authorization.users.AuthenticatedUser;
import edu.harvard.iq.dataverse.settings.FeatureFlags;
import edu.harvard.iq.dataverse.settings.JvmSettings;
import edu.harvard.iq.dataverse.util.SystemConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.ejb.EJB;
import jakarta.ejb.ScheduleExpression;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.Timeout;
import jakarta.ejb.Timer;
import jakarta.ejb.TimerConfig;
import jakarta.ejb.TimerService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

@Singleton
@Startup
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

    @Resource
    TimerService timerService;

    @PostConstruct
    public void init() {
        if (!systemConfig.isTimerServer()) {
            return;
        }

        // Cancel existing timers if any
        Collection<Timer> timers = timerService.getTimers();
        for (Timer timer : timers) {
            if ("DraftReminderTimer".equals(timer.getInfo())) {
                timer.cancel();
            }
        }

        String scheduleStr = JvmSettings.DRAFT_REMINDERS_SCHEDULE.lookupOptional().orElse("1 day");
        
        if (scheduleStr.contains("=")) {
            ScheduleExpression schedule = parseSchedule(scheduleStr);
            timerService.createCalendarTimer(schedule, new TimerConfig("DraftReminderTimer", false));
            logger.info("DraftReminderServiceBean initialized with schedule: " + scheduleStr);
        } else {
            long intervalMillis = parseDurationToMillis(scheduleStr);
            timerService.createIntervalTimer(new Date(), intervalMillis, new TimerConfig("DraftReminderTimer", false));
            logger.info("DraftReminderServiceBean initialized with interval: " + scheduleStr);
        }
    }

    @Timeout
    public void handleTimeout(Timer timer) {
        sendDraftReminders(timer);
    }

    public void sendDraftReminders() {
        sendDraftReminders(null);
    }

    public void sendDraftReminders(Timer timer) {
        if (!FeatureFlags.NOTIFY_ON_UNPUBLISHED_DRAFTS.enabled()) {
            return;
        }

        logger.info("Starting DraftReminderServiceBean.sendDraftReminders");

        String delayStr = JvmSettings.DRAFT_REMINDERS_DELAY.lookupOptional().orElse("6 months");
        String scheduleStr = JvmSettings.DRAFT_REMINDERS_SCHEDULE.lookupOptional().orElse("1 day");

        ZonedDateTime triggerTime = ZonedDateTime.now();
        if (timer != null) {
            try {
                Date nextDate = timer.getNextTimeout();
                if (nextDate != null) {
                    ZonedDateTime next = ZonedDateTime.ofInstant(nextDate.toInstant(), ZoneId.systemDefault());
                    // Since the user wants schedule and window to be the same setting, we use scheduleStr to derive the window.
                    // By subtracting the window from the next scheduled time, we get the current trigger time.
                    if (!scheduleStr.contains("=")) {
                        triggerTime = subtractDuration(next, scheduleStr);
                    } else {
                        // For EJB expressions, we fall back to a 1 day window.
                        triggerTime = next.minusDays(1);
                    }
                }
            } catch (Exception e) {
                logger.warning("Could not calculate trigger time from timer: " + e.getMessage());
            }
        }

        ZonedDateTime endRange = subtractDuration(triggerTime, delayStr);
        ZonedDateTime startRange = subtractDuration(endRange, scheduleStr);

        Timestamp start = Timestamp.from(startRange.toInstant());
        Timestamp end = Timestamp.from(endRange.toInstant());

        logger.info("Querying for drafts modified between " + start + " and " + end);

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
            Set<AuthenticatedUser> editors = permissionService.getDistinctUsersWithPermissionOn(Permission.EditDataset, dataset);
            
            for (AuthenticatedUser editor : editors) {
                userNotificationService.sendNotification(editor, Timestamp.from(triggerTime.toInstant()), UserNotification.Type.UNPUBLISHED_DRAFTS_REMINDER, dv.getId(), null, null, false, delayStr);
            }
        }
        
        logger.info("Finished DraftReminderServiceBean.sendDraftReminders");
    }

    private ScheduleExpression parseSchedule(String scheduleStr) {
        ScheduleExpression expression = new ScheduleExpression();
        String[] parts = scheduleStr.split(";");
        for (String part : parts) {
            String[] kv = part.split("=");
            if (kv.length == 2) {
                String key = kv[0].trim();
                String value = kv[1].trim();
                switch (key.toLowerCase()) {
                    case "second": expression.second(value); break;
                    case "minute": expression.minute(value); break;
                    case "hour": expression.hour(value); break;
                    case "dayofmonth": expression.dayOfMonth(value); break;
                    case "month": expression.month(value); break;
                    case "dayofweek": expression.dayOfWeek(value); break;
                    case "year": expression.year(value); break;
                }
            }
        }
        return expression;
    }

    private ZonedDateTime subtractDuration(ZonedDateTime dateTime, String durationStr) {
        if (durationStr.contains("=")) {
            // This is an EJB schedule expression, not a duration. Fall back to 1 day.
            return dateTime.minusDays(1);
        }
        String[] parts = durationStr.trim().split("\\s+");
        if (parts.length != 2) {
            logger.warning("Invalid duration format: " + durationStr + ". Using default.");
            return dateTime;
        }
        try {
            long amount = Long.parseLong(parts[0]);
            String unit = parts[1].toLowerCase();
            if (unit.endsWith("s")) {
                unit = unit.substring(0, unit.length() - 1);
            }
            switch (unit) {
                case "minute": return dateTime.minus(amount, ChronoUnit.MINUTES);
                case "hour": return dateTime.minus(amount, ChronoUnit.HOURS);
                case "day": return dateTime.minus(amount, ChronoUnit.DAYS);
                case "week": return dateTime.minus(amount, ChronoUnit.WEEKS);
                case "month": return dateTime.minusMonths(amount);
                case "year": return dateTime.minusYears(amount);
                default:
                    logger.warning("Unknown duration unit: " + unit + ". Using default.");
                    return dateTime;
            }
        } catch (NumberFormatException e) {
            logger.warning("Invalid duration amount: " + parts[0] + ". Using default.");
            return dateTime;
        }
    }

    private long parseDurationToMillis(String durationStr) {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime then = subtractDuration(now, durationStr);
        return java.time.Duration.between(then, now).toMillis();
    }
}
