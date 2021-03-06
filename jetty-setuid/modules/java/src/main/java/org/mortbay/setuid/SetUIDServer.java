//========================================================================
//Copyright 2004-2008 Mort Bay Consulting Pty. Ltd.
//------------------------------------------------------------------------
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at 
//http://www.apache.org/licenses/LICENSE-2.0
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.
//========================================================================

package org.mortbay.setuid;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

/**
 * This extension of {@link Server} will make a JNI call to set the unix UID.
 *
 * This can be used to start the server as root so that privileged ports may
 * be accessed and then switch to a non-root user for security.
 * Depending on the value of {@link #setStartServerAsPrivileged(boolean)}, either the
 * server will be started and then the UID set; or the {@link Server#getConnectors()} will be 
 * opened with a call to {@link Connector#open()}, the UID set and then the server is started.
 * The later is the default and avoids any webapplication code being run as a privileged user,
 * but will not work if the application code also needs to open privileged ports.
 *
 *<p>
 * The configured umask is set before the server is started and the configured
 * uid is set after the server is started.
 * </p>
 * @author gregw
 *
 */
public class SetUIDServer extends Server
{
    private static final Logger LOG = Log.getLogger(SetUIDServer.class);

    private int _uid=0;
    private int _gid=0;
    private int _umask=-1;
    private boolean _startServerAsPrivileged;
    private RLimit _rlimitNoFiles = null;
    
    public void setUsername(String username)
    {
        Passwd passwd = SetUID.getpwnam(username);
        _uid = passwd.getPwUid();
    }
    
    public String getUsername()
    {
        Passwd passwd = SetUID.getpwuid(_uid);
        return passwd.getPwName();
    }
    
    public void setGroupname(String groupname)
    {
        Group group = SetUID.getgrnam(groupname);
        _gid = group.getGrGid();
    }
    
    public String getGroupname()
    {
        Group group = SetUID.getgrgid(_gid);
        return group.getGrName();
    }


    public int getUmask ()
    {
        return _umask;
    }
    
    public String getUmaskOctal()
    {
        return Integer.toOctalString(_umask);
    }

    public void setUmask(int umask)
    {
        _umask=umask;
    }

    public void setUmaskOctal(String umask )
    {
        _umask=Integer.parseInt(umask,8);
    }
    
    public int getUid()
    {
        return _uid;
    }

    public void setUid(int uid)
    {
        _uid=uid;
    }
    
    public void setGid(int gid)
    {
        _gid=gid;
    }
    
    public int getGid()
    {
        return _gid;
    }

    public void setRLimitNoFiles(RLimit rlimit)
    {
        _rlimitNoFiles = rlimit;
    }
    
    public RLimit getRLimitNoFiles ()
    {
        return _rlimitNoFiles;
    }
    
    @Override
    protected void doStart() throws Exception
    {
        if (_umask>-1)
        {
            LOG.info("Setting umask=0"+Integer.toString(_umask,8));
            SetUID.setumask(_umask);
        }
        
        if (_rlimitNoFiles != null)
        {
            LOG.info("Current "+SetUID.getrlimitnofiles());
            int success = SetUID.setrlimitnofiles(_rlimitNoFiles);
            if (success < 0)
                LOG.warn("Failed to set rlimit_nofiles, returned status "+success);
            LOG.info("Set "+SetUID.getrlimitnofiles());
        }
        
        if (_startServerAsPrivileged)
        {
            super.doStart();
            if (_gid!=0)
            {
                LOG.info("Setting GID="+_gid);
                SetUID.setgid(_gid);
            }
            if (_uid!=0)
            {
                LOG.info("Setting UID="+_uid);
                SetUID.setuid(_uid);                
                Passwd pw = SetUID.getpwuid(_uid);
                System.setProperty("user.name", pw.getPwName());
                System.setProperty("user.home", pw.getPwDir());
            }
        }
        else
        {
            Connector[] connectors = getConnectors();
            for (int i=0;connectors!=null && i<connectors.length;i++)
                connectors[i].open();
            if (_gid!=0)
            {
                LOG.info("Setting GID="+_gid);
                SetUID.setgid(_gid);
            }
            if (_uid!=0)
            {
                LOG.info("Setting UID="+_uid);
                SetUID.setuid(_uid);
                Passwd pw = SetUID.getpwuid(_uid);
                System.setProperty("user.name", pw.getPwName());
                System.setProperty("user.home", pw.getPwDir());
            }
            super.doStart();
        }
    }

    /* ------------------------------------------------------------ */
    /**
     * @return the startServerAsPrivileged 
     */
    public boolean isStartServerAsPrivileged()
    {
        return _startServerAsPrivileged;
    }

    /* ------------------------------------------------------------ */
    /**
     * @param startContextsAsPrivileged if true, the server is started and then the process UID is switched. If false, the connectors are opened, the UID is switched and then the server is started.
     */
    public void setStartServerAsPrivileged(boolean startContextsAsPrivileged)
    {
        _startServerAsPrivileged=startContextsAsPrivileged;
    }
    
}
