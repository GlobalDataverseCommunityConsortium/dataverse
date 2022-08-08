/*
   Copyright (C) 2005-2012, by the President and Fellows of Harvard College.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

   Dataverse Network - A web application to share, preserve and analyze research data.
   Developed at the Institute for Quantitative Social Science, Harvard University.
   Version 3.0.
*/

package edu.harvard.iq.dataverse.dataaccess;


import edu.harvard.iq.dataverse.DataFile;
import edu.harvard.iq.dataverse.Dataset;
import edu.harvard.iq.dataverse.Dataverse;
import edu.harvard.iq.dataverse.DvObject;
import edu.harvard.iq.dataverse.datavariable.DataVariable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.channels.Channel;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

//import org.apache.commons.httpclient.Header;
//import org.apache.commons.httpclient.methods.GetMethod;


/**
 *
 * @author Leonid Andreev
 * @param <T> what it writes
 */

public abstract class StorageIO<T extends DvObject> {

    public enum FileCopy {
        ORIGINAL,
        PRESERVATION
    };
    
    
    //ToDo - add check to see if the store exists (i.e. it's type/label/other required fields(?) exist? Seems like we should fail early in these cases, with direct/globus/remote stores with directupload/accepting a storageidentifier param being most vulnerable.
    
    //Pre ~v5.12, when ingest occurred, the original file was moved to have a ".orig" extension and the ingested/preservation copy was added using the main storageidentifer
    //Post ~v5.12, the orignal file remains where it is (more efficient in object stores), and the ingested/preservation copy is added with a ".preservation" extension
    //Indivudual store classes can change this mapping as desired
    private static Map<FileCopy, String> defaultFileCopyExtensions = Map.of(FileCopy.ORIGINAL, "orig", FileCopy.PRESERVATION, "preservation");
    
    //All Known aux file extensions produced by ingest
    String[] ingestAuxObjects= {defaultFileCopyExtensions.get(FileCopy.PRESERVATION), "prep", "RSpace", "tab"};
    
    public StorageIO() {
    }
    
    public StorageIO(String storageLocation, String driverId) {
      this.driverId=driverId;
    }

    public StorageIO(T dvObject, DataAccessRequest req, String driverId) {
        this.dvObject = dvObject;
        this.req = req;
        this.driverId=driverId;
        if (this.req == null) {
            this.req = new DataAccessRequest();
        }
        if (this.driverId == null) {
            this.driverId = DataAccess.FILE;
        }
    }

    
    
    // Abstract methods to be implemented by the storage drivers:

    public abstract void open(DataAccessOption... option) throws IOException;

    protected boolean isReadAccess = false;
    protected boolean isWriteAccess = false;
    
    //A  public store is one in which files may be accessible outside Dataverse and therefore accessible without regard to Dataverse's access controls related to restriction and embargoes.
    //Currently, this is just used to warn users at upload time rather than disable restriction/embargo. 
    static protected Map<String, Boolean> driverPublicAccessMap = new HashMap<String, Boolean>();

    public static boolean isPublicStore(String driverId) {
        //Read once and cache
        if(!driverPublicAccessMap.containsKey(driverId)) {
            driverPublicAccessMap.put(driverId, Boolean.parseBoolean(System.getProperty("dataverse.files." + driverId + ".public")));
        }
        return driverPublicAccessMap.get(driverId);
    }

    public boolean canRead() {
        return isReadAccess;
    }

    public boolean canWrite() {
        return isWriteAccess;
    }

    public abstract String getStorageLocation() throws IOException;

    // This method will return a Path, if the storage method is a 
    // local filesystem. Otherwise should throw an IOException. 
    public abstract Path getFileSystemPath() throws IOException;
        
    public abstract boolean exists() throws IOException; 
        
    public abstract void delete() throws IOException;
    
    // this method for copies a local Path (for ex., a
    // temp file, into this DataAccess location):
    public abstract void savePath(Path fileSystemPath) throws IOException;
    
    public void addPreservationVersion(Path fileSystemPath) throws IOException {
        if(isAuxObjectCached(defaultFileCopyExtensions.get(FileCopy.ORIGINAL))|| isAuxObjectCached(defaultFileCopyExtensions.get(FileCopy.PRESERVATION))) {
            throw new IOException("Preservation version already exists");
        }
        savePathAsAux(fileSystemPath, defaultFileCopyExtensions.get(FileCopy.PRESERVATION));
    }
    
    public void removePreservationVersion() throws IOException {
        boolean origExists = isAuxObjectCached(defaultFileCopyExtensions.get(FileCopy.ORIGINAL));
        boolean preserveExists = isAuxObjectCached(defaultFileCopyExtensions.get(FileCopy.PRESERVATION));
        if(!origExists && !preserveExists) {
            throw new IOException("Preservation version doesn't exist");
        }
        if(origExists) {
            revertBackupAsAux(defaultFileCopyExtensions.get(FileCopy.ORIGINAL));
            deleteIngestFiles();
        }
        //Will also remove the PRESERVATION version for v5.12+ datafiles 
        deleteIngestFiles();
    }
    
    protected void deleteIngestFiles() throws IOException {
        List<String> allAuxObjects = listAuxObjects();
        
        for(String ingestAuxObject: ingestAuxObjects) {
            if(allAuxObjects.contains(ingestAuxObject)) {
                deleteAuxObject(ingestAuxObject);
            }
        }
    }

    // same, for an InputStream:
    /**
     * This method copies a local InputStream into this DataAccess location.
     * Note that the S3 driver implementation of this abstract method is problematic, 
     * because S3 cannot save an object of an unknown length. This effectively 
     * nullifies any benefits of streaming; as we cannot start saving until we 
     * have read the entire stream. 
     * One way of solving this would be to buffer the entire stream as byte[], 
     * in memory, then save it... Which of course would be limited by the amount 
     * of memory available, and thus would not work for streams larger than that. 
     * So we have eventually decided to save save the stream to a temp file, then 
     * save to S3. This is slower, but guaranteed to work on any size stream. 
     * An alternative we may want to consider is to not implement this method 
     * in the S3 driver, and make it throw the UnsupportedDataAccessOperationException, 
     * similarly to how we handle attempts to open OutputStreams, in this and the 
     * Swift driver. 
     * (Not an issue in either FileAccessIO or SwiftAccessIO implementations)
     * 
     * @param inputStream InputStream we want to save
     * @param auxItemTag String representing this Auxiliary type ("extension")
     * @throws IOException if anything goes wrong.
    */
    /*
    @Deprecated
    public abstract void saveInputStream(InputStream inputStream) throws IOException;
    @Deprecated
    public abstract void saveInputStream(InputStream inputStream, Long filesize) throws IOException;
    */
    // Auxiliary File Management: (new as of 4.0.2!)
    
    // An "auxiliary object" is an abstraction of the traditional DVN/Dataverse
    // mechanism of storing extra files related to the man StudyFile/DataFile - 
    // such as "saved original" and cached format conversions for tabular files, 
    // thumbnails for images, etc. - in physical files with the same file 
    // name but various reserved extensions. 
   
    //This function retrieves auxiliary files related to datasets, and returns them as inputstream
    public abstract InputStream getAuxFileAsInputStream(String auxItemTag) throws IOException ;
    
    public abstract Channel openAuxChannel(String auxItemTag, DataAccessOption... option) throws IOException;
    
    public abstract long getAuxObjectSize(String auxItemTag) throws IOException; 
    
    public abstract Path getAuxObjectAsPath(String auxItemTag) throws IOException; 
    
    public abstract boolean isAuxObjectCached(String auxItemTag) throws IOException; 
    
    public abstract void revertBackupAsAux(String auxItemTag) throws IOException; 
    
    // this method copies a local filesystem Path into this DataAccess Auxiliary location:
    public abstract void savePathAsAux(Path fileSystemPath, String auxItemTag) throws IOException;
    
    /**
     * This method copies a local InputStream into this DataAccess Auxiliary location.
     * Note that the S3 driver implementation of this abstract method is inefficient, 
     * because S3 cannot save an object of an unknown length. This effectively 
     * nullifies any benefits of streaming; as we cannot start saving until we 
     * have read the entire stream. 
     * One way of solving this would be to buffer the entire stream as byte[], 
     * in memory, then save it... Which of course would be limited by the amount 
     * of memory available, and thus would not work for streams larger than that. 
     * So we have eventually decided to save save the stream to a temp file, then 
     * save to S3. This is slower, but guaranteed to work on any size stream. 
     * An alternative we may want to consider is to not implement this method 
     * in the S3 driver, and make it throw the UnsupportedDataAccessOperationException, 
     * similarly to how we handle attempts to open OutputStreams, in this and the 
     * Swift driver. 
     * (Not an issue in either FileAccessIO or SwiftAccessIO implementations)
     * 
     * @param inputStream InputStream we want to save
     * @param auxItemTag String representing this Auxiliary type ("extension")
     * @throws IOException if anything goes wrong.
    */
    public abstract void saveInputStreamAsAux(InputStream inputStream, String auxItemTag) throws IOException;
    
    /*StorageIO subtypes like S3AccessIO that can make use of the filesize to be more efficient should override this method*/
    public void saveInputStreamAsAux(InputStream inputStream, String auxItemTag, Long filesize) throws IOException {
        saveInputStreamAsAux(inputStream, auxItemTag);
    };
    
    public abstract List<String>listAuxObjects() throws IOException;
    
    public abstract void deleteAuxObject(String auxItemTag) throws IOException; 
    
    public abstract void deleteAllAuxObjects() throws IOException;

    private DataAccessRequest req;
    private InputStream in = null;
    private OutputStream out; 

    protected DvObject dvObject;
    protected String driverId;

    private long size;

    private String mimeType;
    private String fileName;
    private String varHeader;
    private String errorMessage;


    private boolean isLocalFile = false;
    private boolean noVarHeader = false;


    private String remoteUrl;
    protected String remoteStoreName = null;
    protected URL remoteStoreUrl = null;
    

    // getters:
    
    public DvObject getDvObject()
    {
        return dvObject;
    }
    
    public DataFile getDataFile() {
        return (DataFile) dvObject;
    }
    
    public Dataset getDataset() {
        return (Dataset) dvObject;
    }

    public Dataverse getDataverse() {
        return (Dataverse) dvObject;
    }

    public DataAccessRequest getRequest() {
        return req;
    }

    /*public int getStatus() {
        return status;
    }*/

    public long getSize() {
        return size;
    }

    //Convenience method to always get the default - the original for uningested files and the preservation copy if ingested without having to specify a param 
    public InputStream getInputStream() throws IOException {
        return getInputStream(FileCopy.PRESERVATION);
    }
    
    public InputStream getInputStream(FileCopy copy) throws IOException {
        switch (copy) {
        case PRESERVATION:
            // The preservation copy if it exists, otherwise the original/main file
            if (isAuxObjectCached(defaultFileCopyExtensions.get(FileCopy.PRESERVATION))) {
                return getAuxFileAsInputStream(defaultFileCopyExtensions.get(FileCopy.PRESERVATION));
            }
        case ORIGINAL:
            // The original file if it is stored as a .orig aux file or the main file (post
            // ~v5.12)
            if (isAuxObjectCached(defaultFileCopyExtensions.get(FileCopy.PRESERVATION))) {
                return getAuxFileAsInputStream(defaultFileCopyExtensions.get(FileCopy.PRESERVATION));
            }
        default:
            return getMainInputStream();
        }
    }
    
    // This nominally assumes a stream has been opened, e.g. during a call to open(). Stores
    // that don't initialize then (all at this point) should override this method to
    // create the InputStream

    protected InputStream getMainInputStream() throws IOException {
        return in;
    }

    protected OutputStream getOutputStream() throws IOException {
        return out; 
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getFileName() {
        return fileName;
    }

    public String getVarHeader() {
        return varHeader;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getRemoteUrl() {
        return remoteUrl;
    }

    public String getRemoteStoreName() {
        return remoteStoreName;
    }

    public URL getRemoteStoreUrl() {
        return remoteStoreUrl;
    }
    
    /*public GetMethod getHTTPMethod() {
        return method;
    }

    public Header[] getResponseHeaders() {
        return responseHeaders;
    }*/

    public boolean isLocalFile() {
        return isLocalFile;
    }
    
    // "Direct Access" StorageIO is used to access a physical storage 
    // location not associated with any dvObject. (For example, when we 
    // are deleting a physical file left behind by a DataFile that's 
    // already been deleted from the database). 
    public boolean isDirectAccess() {
        return dvObject == null; 
    }

    /*public boolean isRemoteAccess() {
        return isRemoteAccess;
    }*/

    /*public boolean isHttpAccess() {
        return isHttpAccess;
    }*/

    public boolean noVarHeader() {
        return noVarHeader;
    }

    // setters:
    public void setDvObject(T f) {
        dvObject = f;
    }

    public void setRequest(DataAccessRequest dar) {
        req = dar;
    }

    /*public void setStatus(int s) {
        status = s;
    }*/

    public void setSize(long s) {
        size = s;
    }

    protected void setMainInputStream(InputStream is) {
        in = is;
    }
    
    public void setOutputStream(OutputStream os) {
        out = os; 
    } 
    

    public void setMimeType(String mt) {
        mimeType = mt;
    }

    public void setFileName(String fn) {
        fileName = fn;
    }

    public void setVarHeader(String vh) {
        varHeader = vh;
    }

    public void setErrorMessage(String em) {
        errorMessage = em;
    }

    public void setRemoteUrl(String u) {
        remoteUrl = u;
    }

    public void setIsLocalFile(boolean f) {
        isLocalFile = f;
    }


    public void setNoVarHeader(boolean nvh) {
        noVarHeader = nvh;
    }

    public void closeInputStream() {
        if (in != null) {
            try {
                in.close();
            } catch (IOException ex) {
                // we really don't care.
                String eMsg = "Warning: IO exception closing input stream.";
                if (errorMessage == null) {
                    errorMessage = eMsg;
                } else {
                    errorMessage = eMsg + "; " + errorMessage;
                }
            }
        }
    }
    
    public String generateVariableHeader(List<DataVariable> dvs) {
        String varHeader = null;

        if (dvs != null) {
            Iterator<DataVariable> iter = dvs.iterator();
            DataVariable dv;

            if (iter.hasNext()) {
                dv = iter.next();
                varHeader = dv.getName();
            }

            while (iter.hasNext()) {
                dv = iter.next();
                varHeader = varHeader + "\t" + dv.getName();
            }

            varHeader = varHeader + "\n";
        }

        return varHeader;
    }

    protected boolean isWriteAccessRequested(DataAccessOption... options) throws IOException {

        for (DataAccessOption option : options) {
            // In the future we may need to be able to open read-write
            // Channels; no support, or use case for that as of now.

            if (option == DataAccessOption.READ_ACCESS) {
                return false;
            }

            if (option == DataAccessOption.WRITE_ACCESS) {
                return true;
            }
        }

        // By default, we open the file in read mode:
        return false;
    }

	public boolean isBelowIngestSizeLimit() {
		long limit = Long.parseLong(System.getProperty("dataverse.files." + this.driverId + ".ingestsizelimit", "-1"));
		if(limit>0 && getSize()>limit) {
			return false;
		} else {
		    return true;
		}
	}

    public boolean downloadRedirectEnabled() {
        return false;
    }

    public String generateTemporaryDownloadUrl(String auxiliaryTag, String auxiliaryType, String auxiliaryFileName) throws IOException {
        throw new UnsupportedDataAccessOperationException("Direct download not implemented for this storage type");
    }
    
    public static String getDriverPrefix(String driverId) {
        return driverId+ DataAccess.SEPARATOR;
    }
}
