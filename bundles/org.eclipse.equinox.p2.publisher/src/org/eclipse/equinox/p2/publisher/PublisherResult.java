/*******************************************************************************
 * Copyright (c) 2008, 2009 Code 9 and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: 
 *   Code 9 - initial API and implementation
 *   IBM - ongoing development
 ******************************************************************************/
package org.eclipse.equinox.p2.publisher;

import java.util.*;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.equinox.internal.p2.core.helpers.CollectionUtils;
import org.eclipse.equinox.p2.metadata.*;
import org.eclipse.equinox.p2.metadata.query.InstallableUnitQuery;
import org.eclipse.equinox.p2.query.*;

public class PublisherResult implements IPublisherResult {

	final Map<String, Set<IInstallableUnit>> rootIUs = new HashMap<String, Set<IInstallableUnit>>();
	final Map<String, Set<IInstallableUnit>> nonRootIUs = new HashMap<String, Set<IInstallableUnit>>();

	public void addIU(IInstallableUnit iu, String type) {
		if (type == ROOT)
			addIU(rootIUs, iu.getId(), iu);
		if (type == NON_ROOT)
			addIU(nonRootIUs, iu.getId(), iu);
	}

	public void addIUs(Collection<IInstallableUnit> ius, String type) {
		for (IInstallableUnit iu : ius)
			addIU(iu, type);
	}

	private void addIU(Map<String, Set<IInstallableUnit>> map, String id, IInstallableUnit iu) {
		Set<IInstallableUnit> ius = map.get(id);
		if (ius == null) {
			ius = new HashSet<IInstallableUnit>(11);
			map.put(id, ius);
		}
		ius.add(iu);
	}

	public IInstallableUnit getIU(String id, Version version, String type) {
		if ((type == null || type == ROOT) && rootIUs.containsKey(id)) {
			Collection<IInstallableUnit> ius = rootIUs.get(id);
			for (IInstallableUnit iu : ius) {
				if (iu.getVersion().equals(version))
					return iu;
			}
		}
		if ((type == null || type == NON_ROOT) && nonRootIUs.containsKey(id)) {
			Collection<IInstallableUnit> ius = nonRootIUs.get(id);
			for (IInstallableUnit iu : ius) {
				if (iu.getVersion().equals(version))
					return iu;
			}
		}
		return null;
	}

	// TODO this method really should not be needed as it just returns the first
	// matching IU non-deterministically.
	public IInstallableUnit getIU(String id, String type) {
		if (type == null || type == ROOT) {
			Collection<IInstallableUnit> ius = rootIUs.get(id);
			if (ius != null && ius.size() > 0)
				return ius.iterator().next();
		}
		if (type == null || type == NON_ROOT) {
			Collection<IInstallableUnit> ius = nonRootIUs.get(id);
			if (ius != null && ius.size() > 0)
				return ius.iterator().next();
		}
		return null;
	}

	/**
	 * Returns the IUs in this result with the given id.
	 */
	public Collection<IInstallableUnit> getIUs(String id, String type) {
		if (type == null) {
			ArrayList<IInstallableUnit> result = new ArrayList<IInstallableUnit>();
			result.addAll(id == null ? flatten(rootIUs.values()) : getIUs(rootIUs, id));
			result.addAll(id == null ? flatten(nonRootIUs.values()) : getIUs(nonRootIUs, id));
			return result;
		}
		if (type == ROOT)
			return id == null ? flatten(rootIUs.values()) : rootIUs.get(id);
		if (type == NON_ROOT)
			return id == null ? flatten(nonRootIUs.values()) : nonRootIUs.get(id);
		return null;
	}

	private Collection<IInstallableUnit> getIUs(Map<String, Set<IInstallableUnit>> ius, String id) {
		Collection<IInstallableUnit> result = ius.get(id);
		return result == null ? CollectionUtils.<IInstallableUnit> emptyList() : result;
	}

	protected List<IInstallableUnit> flatten(Collection<Set<IInstallableUnit>> values) {
		ArrayList<IInstallableUnit> result = new ArrayList<IInstallableUnit>();
		for (Set<IInstallableUnit> iuSet : values)
			result.addAll(iuSet);
		return result;
	}

	public void merge(IPublisherResult result, int mode) {
		if (mode == MERGE_MATCHING) {
			addIUs(result.getIUs(null, ROOT), ROOT);
			addIUs(result.getIUs(null, NON_ROOT), NON_ROOT);
		} else if (mode == MERGE_ALL_ROOT) {
			addIUs(result.getIUs(null, ROOT), ROOT);
			addIUs(result.getIUs(null, NON_ROOT), ROOT);
		} else if (mode == MERGE_ALL_NON_ROOT) {
			addIUs(result.getIUs(null, ROOT), NON_ROOT);
			addIUs(result.getIUs(null, NON_ROOT), NON_ROOT);
		}
	}

	class QueryableMap implements IQueryable<IInstallableUnit> {
		private Map<String, Set<IInstallableUnit>> map;

		public QueryableMap(Map<String, Set<IInstallableUnit>> map) {
			this.map = map;
		}

		public IQueryResult<IInstallableUnit> query(IQuery<IInstallableUnit> query, IProgressMonitor monitor) {
			return query.perform(flatten(this.map.values()).iterator());
		}
	}

	/**
	 * Queries both the root and non root IUs
	 */
	public IQueryResult<IInstallableUnit> query(IQuery<IInstallableUnit> query, IProgressMonitor monitor) {
		//optimize for installable unit query
		if (query instanceof InstallableUnitQuery) {
			return queryIU((InstallableUnitQuery) query, monitor);
		} else if (query instanceof LimitQuery<?>) {
			return doLimitQuery((LimitQuery<IInstallableUnit>) query, monitor);
		} else if (query instanceof PipedQuery<?>) {
			return doPipedQuery((PipedQuery<IInstallableUnit>) query, monitor);
		}
		IQueryable<IInstallableUnit> nonRootQueryable = new QueryableMap(nonRootIUs);
		IQueryable<IInstallableUnit> rootQueryable = new QueryableMap(rootIUs);
		return new CompoundQueryable<IInstallableUnit>(nonRootQueryable, rootQueryable).query(query, monitor);
	}

	/**
	 * Optimize performance of LimitQuery for cases where we know how to optimize
	 * the child query.
	 */
	private IQueryResult<IInstallableUnit> doLimitQuery(LimitQuery<IInstallableUnit> query, IProgressMonitor monitor) {
		//perform the child query first so it can be optimized
		IQuery<IInstallableUnit> child = query.getQueries().get(0);
		return query(child, monitor).query(query, monitor);
	}

	/**
	 * Optimize performance of PipedQuery for cases where we know how to optimize
	 * the child query.
	 */
	private IQueryResult<IInstallableUnit> doPipedQuery(PipedQuery<IInstallableUnit> query, IProgressMonitor monitor) {
		IQueryResult<IInstallableUnit> last = Collector.emptyCollector();
		List<IQuery<IInstallableUnit>> queries = query.getQueries();
		if (!queries.isEmpty()) {
			//call our method to do optimized execution of first query
			last = query(queries.get(0), monitor);
			for (int i = 1; i < queries.size(); i++) {
				if (last.isEmpty())
					break;
				//Can't optimize the rest, but likely at this point the result set is much smaller
				last = queries.get(i).perform(last.iterator());
			}
		}
		return last;
	}

	private IQueryResult<IInstallableUnit> queryIU(InstallableUnitQuery query, IProgressMonitor monitor) {
		Collector<IInstallableUnit> result = new Collector<IInstallableUnit>();
		Collection<IInstallableUnit> matches = getIUs(query.getId(), null);
		VersionRange queryRange = query.getRange();
		for (IInstallableUnit match : matches) {
			if (queryRange == null || queryRange.isIncluded(match.getVersion()))
				if (!result.accept(match))
					break;
		}
		return result;
	}
}