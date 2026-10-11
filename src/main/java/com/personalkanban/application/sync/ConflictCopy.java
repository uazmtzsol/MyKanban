package com.personalkanban.application.sync;

import com.personalkanban.domain.board.BoardMemento;

import java.util.List;

/**
 * On-disk shape of a conflict copy: metadata + manifest of conflicting fields
 * + the full losing snapshot. Lives in the application layer (like the other
 * sync DTOs) so readers such as
 * {@link com.personalkanban.application.CurrentConflicts} never depend on an
 * infrastructure adapter; the JSON field names are the contract with files
 * already written by {@code JsonConflictCopyStore}.
 */
public record ConflictCopy(String boardId, String boardName, long savedAt,
                           List<SyncConflict> conflicts, BoardMemento snapshot) {
}
