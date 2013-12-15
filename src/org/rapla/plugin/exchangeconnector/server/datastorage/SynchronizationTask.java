package org.rapla.plugin.exchangeconnector.server.datastorage;

import java.io.Serializable;

import org.rapla.components.util.TimeInterval;
import org.rapla.entities.User;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.storage.RefEntity;

public class SynchronizationTask implements Serializable
{
	public enum SyncStatus implements Serializable
	{
		toUpdate
		,toDelete
		,toReplace
		,synched
	}
	private static final long serialVersionUID = 219323872273312836L;
	Comparable userId;
	Comparable appointmentId;
	String exchangeAppointmentId;
	
	TimeInterval syncInterval;
	SyncStatus status;
	
	public SynchronizationTask(Appointment appointment, User user) {
		userId = ((RefEntity<?>) user).getId();
		appointmentId = ((RefEntity<?>) appointment).getId();
		status = SyncStatus.toUpdate;
	}

	public Comparable getUserId() {
		return userId;
	}

	public Comparable getAppointmentId() {
		return appointmentId;
	}
	
	public String getExchangeAppointmentId() {
		return exchangeAppointmentId;
	}
	
	public void setExchangeAppointmentId(String exchangeAppointmentId) {
		this.exchangeAppointmentId = exchangeAppointmentId;
	}

	public TimeInterval getSyncInterval() {
		return syncInterval;
	}
	public void setSyncInterval(TimeInterval syncInterval) {
		this.syncInterval = syncInterval;
	}
	public SyncStatus getStatus() {
		return status;
	}
	public void setStatus(SyncStatus status) {
		this.status = status;
	}

	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((appointmentId == null) ? 0 : appointmentId.hashCode());
		result = prime * result + ((userId == null) ? 0 : userId.hashCode());
		return result;
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SynchronizationTask other = (SynchronizationTask) obj;
		if (appointmentId == null) {
			if (other.appointmentId != null)
				return false;
		} else if (!appointmentId.equals(other.appointmentId))
			return false;
		if (userId == null) {
			if (other.userId != null)
				return false;
		} else if (!userId.equals(other.userId))
			return false;
		return true;
	}
	
	@Override
	public String toString() {
		return "SynchronizationTask [userId=" + userId + ", appointmentId="
				+ appointmentId + ", exchangeAppointmentId="
				+ exchangeAppointmentId + ", syncInterval=" + syncInterval
				+ ", status=" + status + "]";
	}

	public boolean matches(Appointment appointment, User user) {
		if (!matches(user))
		{
			return false;
		}
		return matches(appointment);
	}

	public boolean matches(Appointment appointment) {
		Comparable other_appointmentId = ((RefEntity<?>) appointment).getId();
		if (appointmentId == null) {
			if (other_appointmentId != null)
				return false;
		} else if (!appointmentId.equals(other_appointmentId))
		{
			return false;
		}
		return true;
	}

	public boolean matches(User user) {
		Comparable other_userId = ((RefEntity<?>) user).getId();
		if (userId == null) {
			if (other_userId != null)
				return false;
		} 
		else if (!userId.equals(other_userId))
		{
			return false;
		}
		return true;
	}

	
}