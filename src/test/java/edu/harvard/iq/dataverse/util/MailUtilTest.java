package edu.harvard.iq.dataverse.util;

import edu.harvard.iq.dataverse.UserNotification;
import edu.harvard.iq.dataverse.branding.BrandingUtil;

import static org.junit.Assert.assertEquals;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.After;
import org.junit.Before;

public class MailUtilTest {

    private String rootDataverseName;
    UserNotification userNotification = new UserNotification();

    private MailUtil mailUtil;

    @After
    public void tearDown() {
      this.mailUtil = null;
    }
    
    @Before
    public void setUp() {
        rootDataverseName = "LibraScholar";
        userNotification = new UserNotification();
        this.mailUtil = new MailUtil(new BrandingUtil());

    }

    @Test
    public void testParseSystemAddress() {
        assertEquals("support@librascholar.edu", mailUtil.parseSystemAddress("support@librascholar.edu").getAddress());
        assertEquals("support@librascholar.edu", mailUtil.parseSystemAddress("LibraScholar Support Team <support@librascholar.edu>").getAddress());
        assertEquals("LibraScholar Support Team", mailUtil.parseSystemAddress("LibraScholar Support Team <support@librascholar.edu>").getPersonal());
        assertEquals("support@librascholar.edu", mailUtil.parseSystemAddress("\"LibraScholar Support Team\" <support@librascholar.edu>").getAddress());
        assertEquals("LibraScholar Support Team", mailUtil.parseSystemAddress("\"LibraScholar Support Team\" <support@librascholar.edu>").getPersonal());
        assertEquals(null, mailUtil.parseSystemAddress(null));
        assertEquals(null, mailUtil.parseSystemAddress(""));
        assertEquals(null, mailUtil.parseSystemAddress("LibraScholar Support Team support@librascholar.edu"));
        assertEquals(null, mailUtil.parseSystemAddress("\"LibraScholar Support Team <support@librascholar.edu>"));
        assertEquals(null, mailUtil.parseSystemAddress("support1@dataverse.org, support@librascholar.edu"));
    }

    @Test
    public void testSubjectCreateAccount() {
        userNotification.setType(UserNotification.Type.CREATEACC);
        assertEquals("LibraScholar: Your account has been created", mailUtil.getSubjectTextBasedOnNotification(userNotification, rootDataverseName, null));
    }

    @Test
    public void testSubjectAssignRole() {
        userNotification.setType(UserNotification.Type.ASSIGNROLE);
        assertEquals("LibraScholar: You have been assigned a role", mailUtil.getSubjectTextBasedOnNotification(userNotification, rootDataverseName, null));
    }

    @Test
    public void testSubjectCreateDataverse() {
        userNotification.setType(UserNotification.Type.CREATEDV);
        assertEquals("LibraScholar: Your dataverse has been created", mailUtil.getSubjectTextBasedOnNotification(userNotification, rootDataverseName, null));
    }
    
    @Test
    public void testSubjectRevokeRole() {
        userNotification.setType(UserNotification.Type.REVOKEROLE);
        assertEquals("LibraScholar: Your role has been revoked", mailUtil.getSubjectTextBasedOnNotification(userNotification, rootDataverseName, null));
    }
    
    @Test
    public void testSubjectRequestFileAccess() {
        userNotification.setType(UserNotification.Type.REQUESTFILEACCESS);
        assertEquals("LibraScholar: Access has been requested for a restricted file", mailUtil.getSubjectTextBasedOnNotification(userNotification, rootDataverseName, null));
    }
    
    @Test
    public void testSubjectGrantFileAccess() {
        userNotification.setType(UserNotification.Type.GRANTFILEACCESS);
        assertEquals("LibraScholar: You have been granted access to a restricted file", mailUtil.getSubjectTextBasedOnNotification(userNotification, rootDataverseName, null));
    }
    
    @Test
    public void testSubjectRejectFileAccess() {
        userNotification.setType(UserNotification.Type.REJECTFILEACCESS);
        assertEquals("LibraScholar: Your request for access to a restricted file has been rejected", mailUtil.getSubjectTextBasedOnNotification(userNotification, rootDataverseName, null));
    }

    @Test
    public void testSubjectCreateDataset() {
        userNotification.setType(UserNotification.Type.CREATEDS);
        assertEquals("LibraScholar: Your dataset has been created", mailUtil.getSubjectTextBasedOnNotification(userNotification, rootDataverseName, null));
    }
    
    @Test
    public void testSubjectSubmittedDS() {
        userNotification.setType(UserNotification.Type.SUBMITTEDDS);
        assertEquals("LibraScholar: Your dataset has been submitted for review", mailUtil.getSubjectTextBasedOnNotification(userNotification, rootDataverseName, null));
    }
    
    @Test
    public void testSubjectPublishedDS() {
        userNotification.setType(UserNotification.Type.PUBLISHEDDS);
        assertEquals("LibraScholar: Your dataset has been published", mailUtil.getSubjectTextBasedOnNotification(userNotification, rootDataverseName, null));
    }
    
    @Test
    public void testSubjectReturnedDS() {
        userNotification.setType(UserNotification.Type.RETURNEDDS);
        assertEquals("LibraScholar: Your dataset has been returned", mailUtil.getSubjectTextBasedOnNotification(userNotification, rootDataverseName, null));
    }
    
    @Test
    public void testSubjectChecksumFail() {
        userNotification.setType(UserNotification.Type.CHECKSUMFAIL);
        assertEquals("LibraScholar: Your upload failed checksum validation", mailUtil.getSubjectTextBasedOnNotification(userNotification, rootDataverseName, null));
    }
    
    @Test
    public void testSubjectFileSystemImport() {
        userNotification.setType(UserNotification.Type.FILESYSTEMIMPORT);
        //TODO SEK add a dataset version to get the Dataset Title which is actually used in the subject now
        assertEquals("Dataset LibraScholar has been successfully uploaded and verified", mailUtil.getSubjectTextBasedOnNotification(userNotification, rootDataverseName , null));
    }
    
    @Test
    public void testSubjectChecksumImport() {
        userNotification.setType(UserNotification.Type.CHECKSUMIMPORT);
        assertEquals("LibraScholar: Your file checksum job has completed", mailUtil.getSubjectTextBasedOnNotification(userNotification, rootDataverseName, null));
    }

    @Test
    public void testSubjectConfirmEmail() {
        userNotification.setType(UserNotification.Type.CONFIRMEMAIL);
        assertEquals("LibraScholar: Verify your email address", mailUtil.getSubjectTextBasedOnNotification(userNotification, rootDataverseName, null));
    }
}
