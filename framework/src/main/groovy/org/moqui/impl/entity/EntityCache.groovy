/*
 * This Work is in the public domain and is provided on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE,
 * NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 * You are solely responsible for determining the appropriateness of using
 * this Work and assume any risks associated with your use of this Work.
 *
 * This Work includes contributions authored by David E. Jones, not as a
 * "work for hire", who hereby disclaims any copyright to the same.
 */
package org.moqui.impl.entity

import net.sf.ehcache.Ehcache
import org.moqui.context.Cache
import org.moqui.entity.EntityCondition
import org.moqui.entity.EntityValue
import org.moqui.impl.context.CacheImpl
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class EntityCache {
    protected final static Logger logger = LoggerFactory.getLogger(EntityCache.class)

    protected final EntityFacadeImpl efi

    EntityCache(EntityFacadeImpl efi) {
        this.efi = efi
    }

    // EntityFacadeImpl getEfi() { return efi }

    CacheImpl getCacheOne(String entityName) { return efi.ecfi.getCacheFacade().getCacheImpl("entity.${efi.tenantId}.one.${entityName}") }
    private CacheImpl getCacheOneRa(String entityName) { return efi.ecfi.getCacheFacade().getCacheImpl("entity.${efi.tenantId}.one_ra.${entityName}") }
    private CacheImpl getCacheOneBf() { return efi.ecfi.getCacheFacade().getCacheImpl("entity.${efi.tenantId}.one_bf") }
    CacheImpl getCacheList(String entityName) { return efi.ecfi.getCacheFacade().getCacheImpl("entity.${efi.tenantId}.list.${entityName}") }
    private CacheImpl getCacheListRa(String entityName) { return efi.ecfi.getCacheFacade().getCacheImpl("entity.${efi.tenantId}.list_ra.${entityName}") }
    private CacheImpl getCacheCount(String entityName) { return efi.ecfi.getCacheFacade().getCacheImpl("entity.${efi.tenantId}.count.${entityName}") }

    void clearCacheForValue(EntityValueBase evb, boolean isCreate) {
        try {
            EntityDefinition ed = evb.getEntityDefinition()
            if (ed.getEntityNode()."@use-cache" == "never") return
            String fullEntityName = ed.getFullEntityName()
            EntityCondition pkCondition = efi.getConditionFactory().makeCondition(evb.getPrimaryKeys())

            // clear one cache
            if (efi.ecfi.getCacheFacade().cacheExists("entity.${efi.tenantId}.one.${fullEntityName}")) {
                Cache entityOneCache = getCacheOne(fullEntityName)
                Ehcache eocEhc = entityOneCache.getInternalCache()
                // clear by PK, most common scenario
                eocEhc.remove(pkCondition)

                // NOTE: these two have to be done whether or not it is a create because of non-pk updates, etc
                // see if there are any one RA entries
                Cache oneRaCache = getCacheOneRa(fullEntityName)
                if (oneRaCache.containsKey(pkCondition)) {
                    List<EntityCondition> raKeyList = (List<EntityCondition>) oneRaCache.get(pkCondition)
                    for (EntityCondition ec in raKeyList) {
                        eocEhc.remove(ec)
                    }
                    // we've cleared all entries that this was referring to, so clean it out too
                    oneRaCache.remove(pkCondition)
                }
                // see if there are any cached entries with no result using the bf (brute-force) matching
                Cache oneBfCache = getCacheOneBf()
                Set<EntityCondition> bfKeySet = (Set<EntityCondition>) oneBfCache.get(fullEntityName)
                if (bfKeySet) {
                    Set<EntityCondition> keysToRemove = new HashSet<EntityCondition>()
                    for (EntityCondition bfKey in bfKeySet) {
                        if (bfKey.mapMatches(evb)) {
                            eocEhc.remove(bfKey)
                            keysToRemove.add(bfKey)
                        }
                    }
                    for (EntityCondition key in keysToRemove) bfKeySet.remove(key)
                }
            }

            // logger.warn("============= clearing list for entity ${fullEntityName}, for pkCondition [${pkCondition}] cacheExists=${efi.ecfi.getCacheFacade().cacheExists("entity.${efi.tenantId}.list.${fullEntityName}")}")
            // clear list cache, use reverse-associative Map (also a Cache)
            if (efi.ecfi.getCacheFacade().cacheExists("entity.${efi.tenantId}.list.${fullEntityName}")) {
                // if this was a create the RA cache won't help, so go through EACH entry and see if it matches the created value
                if (isCreate) {
                    CacheImpl entityListCache = getCacheList(fullEntityName)
                    Ehcache elEhc = entityListCache.getInternalCache()
                    List<EntityCondition> elEhcKeys = (List<EntityCondition>) elEhc.getKeys()
                    for (EntityCondition ec in elEhcKeys) {
                        // any way to efficiently clear out the RA cache for these? for now just leave and they are handled eventually
                        if (ec.mapMatches(evb)) elEhc.remove(ec)
                    }
                } else {
                    Cache listRaCache = getCacheListRa(fullEntityName)
                    // logger.warn("============= clearing list for entity ${fullEntityName}, for pkCondition [${pkCondition}] listRaCache=${listRaCache}")
                    if (listRaCache.containsKey(pkCondition)) {
                        List raKeyList = (List) listRaCache.get(pkCondition)
                        // logger.warn("============= for entity ${fullEntityName}, for pkCondition [${pkCondition}], raKeyList for clear=${raKeyList}")
                        CacheImpl entityListCache = getCacheList(fullEntityName)
                        Ehcache elcEhc = entityListCache.getInternalCache()
                        for (Object raKey in raKeyList) {
                            // logger.warn("============= for entity ${fullEntityName}, removing raKey=${raKey} from ${entityListCache.getName()}")
                            if (raKey instanceof EntityCondition) {
                                EntityCondition ec = (EntityCondition) raKey
                                // this may have already been cleared, but it is a waste of time to check for that explicitly
                                elcEhc.remove(ec)
                            } else {
                                Map viewEcMap = (Map) raKey
                                CacheImpl viewEntityListCache = getCacheList((String) viewEcMap.ven)
                                Ehcache velcEhc = viewEntityListCache.getInternalCache()
                                // this may have already been cleared, but it is a waste of time to check for that explicitly
                                velcEhc.remove(viewEcMap.ec)
                            }
                        }
                        // we've cleared all entries that this was referring to, so clean it out too
                        listRaCache.remove(pkCondition)
                    }
                }
            }

            // clear count cache (no RA because we only have a count to work with, just match by condition)
            if (efi.ecfi.getCacheFacade().cacheExists("entity.${efi.tenantId}.count.${fullEntityName}")) {
                CacheImpl entityCountCache = getCacheCount(fullEntityName)
                Ehcache ecEhc = entityCountCache.getInternalCache()
                List<EntityCondition> ecEhcKeys = (List<EntityCondition>) ecEhc.getKeys()
                for (EntityCondition ec in ecEhcKeys) {
                    if (ec.mapMatches(evb)) ecEhc.remove(ec)
                }
            }
        } catch (Throwable t) {
            logger.error("Suppressed error in entity cache clearing [${evb.getEntityName()}; ${isCreate ? 'create' : 'non-create'}]", t)
        }
    }
    void registerCacheOneRa(String entityName, EntityCondition ec, EntityValueBase evb) {
        EntityDefinition ed = efi.getEntityDefinition(entityName)
        // don't skip it for null values because we're caching those too: if (evb == null) return
        if (evb == null) {
            // can't use RA cache because we don't know the PK, so use a brute-force cache but keep it separate to perform better
            Cache oneBfCache = getCacheOneBf()
            Set bfKeySet = (Set) oneBfCache.get(ed.getFullEntityName())
            if (bfKeySet == null) {
                bfKeySet = new HashSet()
                oneBfCache.put(entityName, bfKeySet)
            }
            bfKeySet.add(ec)
        } else {
            Cache oneRaCache = getCacheOneRa(ed.getFullEntityName())
            EntityCondition pkCondition = efi.getConditionFactory().makeCondition(evb.getPrimaryKeys())
            // if the condition matches the primary key, no need for an RA entry
            if (pkCondition == ec) return
            List raKeyList = (List) oneRaCache.get(pkCondition)
            if (raKeyList == null) {
                raKeyList = new ArrayList()
                oneRaCache.put(pkCondition, raKeyList)
            }
            raKeyList.add(ec)
        }
    }
    void registerCacheListRa(String entityName, EntityCondition ec, EntityListImpl eli) {
        EntityDefinition ed = efi.getEntityDefinition(entityName)
        if (ed.isViewEntity()) {
            // go through each member-entity
            for (Node memberEntityNode in ed.getEntityNode()."member-entity") {
                Map mePkFieldToAliasNameMap = ed.getMePkFieldToAliasNameMap((String) memberEntityNode."@entity-alias")

                // logger.warn("TOREMOVE for view-entity ${entityName}, member-entity ${memberEntityNode.'@entity-name'}, got PK field to alias map: ${mePkFieldToAliasNameMap}")

                // create EntityCondition with pk fields
                // store with main ec with view-entity name in a RA cache for view entities for the member-entity name
                // with cache key of member-entity PK EntityCondition obj
                EntityDefinition memberEd = efi.getEntityDefinition((String) memberEntityNode.'@entity-name')
                Cache listViewRaCache = getCacheListRa(memberEd.getFullEntityName())
                for (EntityValue ev in eli) {
                    Map pkCondMap = new HashMap()
                    for (Map.Entry mePkEntry in mePkFieldToAliasNameMap) pkCondMap.put(mePkEntry.getKey(), ev.get(mePkEntry.getValue()))
                    EntityCondition pkCondition = efi.getConditionFactory().makeCondition(pkCondMap)
                    List raKeyList = (List) listViewRaCache.get(pkCondition)
                    if (!raKeyList) {
                        raKeyList = new ArrayList()
                        listViewRaCache.put(pkCondition, raKeyList)
                    }
                    raKeyList.add([ven:entityName, ec:ec])
                    // logger.warn("TOREMOVE for view-entity ${entityName}, member-entity ${memberEntityNode.'@entity-name'}, for pkCondition [${pkCondition}], raKeyList after add=${raKeyList}")
                }
            }
        } else {
            Cache listRaCache = getCacheListRa(ed.getFullEntityName())
            for (EntityValue ev in eli) {
                EntityCondition pkCondition = efi.getConditionFactory().makeCondition(ev.getPrimaryKeys())
                List raKeyList = (List) listRaCache.get(pkCondition)
                if (!raKeyList) {
                    raKeyList = new ArrayList()
                    listRaCache.put(pkCondition, raKeyList)
                }
                raKeyList.add(ec)
            }
        }
    }
}
