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

import de.topobyte.osm4j.core.model.iface.OsmRelation;

public class RelationGroupMultiple implements RelationGroup
{

	private long id;
	private Collection<OsmRelation> relations;

	public RelationGroupMultiple(long id, Collection<OsmRelation> relations)
	{
		this.id = id;
		this.relations = relations;
	}

	@Override
	public long getId()
	{
		return id;
	}

	@Override
	public Collection<OsmRelation> getRelations()
	{
		return relations;
	}

}
