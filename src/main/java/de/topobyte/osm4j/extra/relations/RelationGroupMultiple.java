// Copyright 2015 Sebastian Kuerten
//
// This file is part of osm4j.
//
// osm4j is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// osm4j is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public License
// along with osm4j. If not, see <http://www.gnu.org/licenses/>.

package de.topobyte.osm4j.extra.relations;

import java.util.Collection;
import java.util.Set;

import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmRelation;
import de.topobyte.osm4j.core.resolve.EntityNotFoundException;
import de.topobyte.osm4j.core.resolve.OsmEntityProvider;

public class RelationGroupMultiple implements RelationGroup
{

	private Collection<OsmRelation> relations;

	public RelationGroupMultiple(Collection<OsmRelation> relations)
	{
		this.relations = relations;
	}

	@Override
	public Set<OsmNode> findNodes(OsmEntityProvider entityProvider)
			throws EntityNotFoundException
	{
		return RelationUtil.findNodes(relations, entityProvider);
	}

	@Override
	public Collection<OsmRelation> getRelations()
	{
		return relations;
	}

}
