package org.terifan.net.ftp.client;


/**
 * Listener that receives progress information from file upload/download
 * actions.
 */
@FunctionalInterface
public interface ProgressListener
{
	/**
	 * Method progressChanged will receive the TRANSFER_COMPLETED value when the
	 * upload/download has completed.
	 */
	long TRANSFER_COMPLETED = Long.MAX_VALUE;

	/**
	 * Method progressChanged will receive the TRANSFER_STARTED value when the
	 * upload/download has starts before any actual progress information exists.
	 */
	long TRANSFER_STARTED = Long.MIN_VALUE;

	/**
	 * This method is called by the DataSocket class allowing a client to update
	 * an user interface with current progress.
	 *
	 * @param aTransferedCount
	 *    number of bytes transfered or TRANSFER_COMPLETED if transfer has
	 *    completed or TRANSFER_STARTED if transfer has started.
	 */
	void progressChanged(long aTransferedCount);
}