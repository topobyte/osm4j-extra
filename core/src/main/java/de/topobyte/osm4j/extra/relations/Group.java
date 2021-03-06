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

import com.slimjars.dist.gnu.trove.set.TLongSet;

public class Group
{

	private long start;
	private TLongSet relationIds;
	private int numRelations;
	private int numMembers;

	public Group(long start, TLongSet relationIds)
	{
		this.start = start;
		this.relationIds = relationIds;
		numRelations = relationIds.size();
	}

	public long getStart()
	{
		return start;
	}

	public TLongSet getRelationIds()
	{
		return relationIds;
	}

	public int getNumRelations()
	{
		return numRelations;
	}

	public int getNumMembers()
	{
		return numMembers;
	}

	public void setNumMembers(int numMembers)
	{
		this.numMembers = numMembers;
	}

}
