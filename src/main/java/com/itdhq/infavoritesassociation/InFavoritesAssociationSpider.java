package com.itdhq.infavoritesassociation;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.module.AbstractModuleComponent;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.favourites.FavouritesService;
import org.alfresco.service.cmr.preference.PreferenceService;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.search.ResultSet;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.cmr.security.AuthenticationService;
import org.alfresco.service.cmr.security.PersonService;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.namespace.RegexQNamePattern;
import org.apache.log4j.Logger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by malchun on 3/27/16.
 */
public class InFavoritesAssociationSpider
    extends AbstractModuleComponent
{
    private static Logger logger = Logger.getLogger(InFavoritesAssociationSpider.class);
    protected NodeService nodeService;
    protected SearchService searchService;
    protected PersonService personService;
    protected PreferenceService preferenceService;
    protected AuthenticationService authenticationService;

    public void setNodeService(NodeService nodeService) { this.nodeService = nodeService; }
    public void setPreferenceService(PreferenceService preferenceService) { this.preferenceService = preferenceService; }
    public void setPersonService(PersonService personService) { this.personService = personService; }
    public void setSearchService(SearchService searchService) { this.searchService = searchService; }
    public void setAuthenticationService(AuthenticationService authenticationService) { this.authenticationService = authenticationService; }

    public static final QName infavorites_aspect_qname = QName.createQName("http://itdhq.com/prefix/infav", "infavorites_association_aspect");
    public static final QName infavorites_documents_association_qname = QName.createQName("http://itdhq.com/prefix/infav", "infavorites_documents_association");
    public static final QName infavorites_folders_association_qname = QName.createQName("http://itdhq.com/prefix/infav", "infavorites_folders_association");

    private NodeRef test = null;


    @Override
    protected void executeInternal()
    {
        logger.debug("Spider is online");
        Thread myThready = new Thread(new Runnable()
        {
            public void run() //Этот метод будет выполняться в побочном потоке
            {
                logger.debug("Waiting solr");
                //logger.debug("authenticated");

                int i = 0;
                while (null == test)
                {
                    ++i;
                    logger.debug("Try N " + i);
                    try {
                        AuthenticationUtil.runAsSystem(new AuthenticationUtil.RunAsWork<Void>() {
                            @Override
                            public Void doWork() throws Exception {
                                ResultSet query = searchService.query(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, SearchService.LANGUAGE_LUCENE, "PATH:\"/app:company_home/app:dictionary\"");
                                ResultSet query1 = searchService.query(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, SearchService.LANGUAGE_LUCENE, "PATH:\"/app:company_home/app:guest_home\"");
                                test = (null != query1.getNodeRef(0)) ? query1.getNodeRef(0) : query.getNodeRef(0);
                                return null;
                            }
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    try {
                        logger.debug("still waiting");
                        Thread.sleep(50000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                processSystem();
            }
        });
        myThready.start();	//Запуск потока

    }


    protected void processSystem()
    {
        logger.info("In favorites spider began processing.");
        AuthenticationUtil.runAsSystem(new AuthenticationUtil.RunAsWork<Void>() {
            @Override
            public Void doWork() throws Exception {
                NodeRef container = personService.getPeopleContainer();
                List<ChildAssociationRef> childRefs = nodeService.getChildAssocs(container, ContentModel.ASSOC_CHILDREN, RegexQNamePattern.MATCH_ALL, false);
                for (ChildAssociationRef i : childRefs) {

                    NodeRef person = i.getChildRef();
                    logger.debug("person gotten");
                    if (!nodeService.getAspects(person).contains(InFavoritesAssociationSpider.infavorites_aspect_qname)) {
                        nodeService.addAspect(person, InFavoritesAssociationSpider.infavorites_aspect_qname, new HashMap<QName, Serializable>());
                    }

                    setFavoriteAssociations(person, infavorites_documents_association_qname, "org.alfresco.share.documents.favourites");
                    setFavoriteAssociations(person, infavorites_folders_association_qname, "org.alfresco.share.folders.favourites");
                }
                //this.setProcessed();
                return null;
            }
        });
        logger.info("In favorites spider succeeded in processing favorites.");

    }

    public void setFavoriteAssociations(NodeRef person, QName associtation, String type)
    {
        logger.debug("InFavoriteAssociationSpider.setFavoriteAssociations");
        if (null != preferenceService.getPreference((String) nodeService.getProperty(person, ContentModel.PROP_USERNAME), type)) {
            logger.debug(person.toString() + "   " + (String) nodeService.getProperty(person, ContentModel.PROP_USERNAME));
            String favorite_documents = preferenceService.getPreference((String) nodeService.getProperty(person, ContentModel.PROP_USERNAME), type).toString();
            if(!favorite_documents.isEmpty()) {
                List<NodeRef> favoriteRefs = new ArrayList<>();
                for (String favorite: favorite_documents.split(","))
                {
                    try {
                        NodeRef favoriteRef = new NodeRef(favorite);
                        nodeService.getProperties(favoriteRef);
                        favoriteRefs.add(favoriteRef); //new NodeRef(favorite));
                    } catch (Exception e) {
                        e.printStackTrace();;
                    }
                }
                logger.debug(favoriteRefs);
                for (NodeRef i: favoriteRefs) {
                    logger.debug(nodeService.getProperties(i));
                }
                nodeService.setAssociations(person, associtation, favoriteRefs);
            } else {
                nodeService.setAssociations(person, associtation, new ArrayList<NodeRef>());
            }
        }
    }

/*

    public boolean hasNotProcessedYet()
    {

        return true;
    }

    private void setProcessed()
    {
        AuthenticationUtil.runAsSystem(new AuthenticationUtil.RunAsWork<Void>() {
            @Override
            public Void doWork()
            {
                ResultSet query = null;
                NodeRef rootNode = null;

                try
                {
                    query = searchService.query(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, SearchService.LANGUAGE_LUCENE, "PATH:\"/app\\:company_home\"");
                    rootNode = query.getNodeRef(0);
                }
                finally
                {
                    if (null != query)
                    {
                        query.close();
                    }
                }

                Map<QName, Serializable> properties = new HashMap<>();
                properties.put(ContentModel.PROP_NAME, "favorites_processed.lock");
                NodeRef lock_node = nodeService.createNode(rootNode, ContentModel.ASSOC_CONTAINS, QName.createQName(ContentModel.USER_MODEL_URI, "favorites_processed.lock"),
                        ContentModel.TYPE_CONTENT, properties).getChildRef();

                return null;
            }
        });
    }

    /*
    private Map<FavouritesService.Type, PrefKeys> prefKeys;

    //-------
    prefKeys = new HashMap<>();
    this.prefKeys.put(FavouritesService.Type.SITE, new PrefKeys("org.alfresco.share.sites.favourites.", "org.alfresco.ext.sites.favourites."));
    this.prefKeys.put(FavouritesService.Type.FILE, new PrefKeys("org.alfresco.share.documents.favourites", "org.alfresco.ext.documents.favourites."));
    this.prefKeys.put(FavouritesService.Type.FOLDER, new PrefKeys("org.alfresco.share.folders.favourites", "org.alfresco.ext.folders.favourites."));
    // ---------

    private static class PrefKeys
    {
        private String sharePrefKey;
        private String alfrescoPrefKey;

        public PrefKeys(String sharePrefKey, String alfrescoPrefKey)
        {
            super();
            this.sharePrefKey = sharePrefKey;
            this.alfrescoPrefKey = alfrescoPrefKey;
        }

        public String getSharePrefKey()
        {
            return sharePrefKey;
        }

        public String getAlfrescoPrefKey()
        {
            return alfrescoPrefKey;
        }
    }

    private PrefKeys getPrefKeys(FavouritesService.Type type)
    {
        PrefKeys prefKey = prefKeys.get(type);
        return prefKey;
    }
*/
}
