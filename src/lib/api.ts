export type Folder = {
  id: number
  path: string
  file_count: number
  last_scan_at: string | null
  scan_status: 'running' | 'completed' | 'failed' | null
  is_scanning: boolean
}

export type Scan = {
  id: number
  folder_id: number
  folder_path: string
  status: 'running' | 'completed' | 'failed'
  started_at: string
  completed_at: string | null
  discovered: number
  indexed: number
  errors: number
  message: string | null
}

export type Stats = {
  total: number
  pdf: number
  epub: number
  folders: number
  exact_duplicate_groups: number
  possible_duplicate_groups: number
}

export type Book = {
  id: number
  folder_id?: number
  path: string
  filename: string
  file_type: 'pdf' | 'epub'
  title: string
  author: string | null
  isbn?: string | null
  size_bytes: number
  sha256: string
  scan_error: string | null
  updated_at?: string
}

export type DuplicateGroup = {
  key: string
  file_count: number
  books: Book[]
}

type BooksResponse = {
  items: Book[]
  total: number
}

type DuplicatesResponse = {
  kind: 'exact' | 'possible'
  groups: DuplicateGroup[]
  total: number
}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    headers: { 'Content-Type': 'application/json' },
    ...init,
  })

  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new Error(body?.detail ?? `Request failed (${response.status})`)
  }

  if (response.status === 204) {
    return undefined as T
  }
  return response.json() as Promise<T>
}

export const api = {
  stats: () => request<Stats>('/api/stats'),
  folders: () => request<Folder[]>('/api/folders'),
  scans: () => request<Scan[]>('/api/scans?limit=8'),
  books: (query = '', fileType = 'all', offset = 0, limit = 100) => {
    const params = new URLSearchParams({
      q: query,
      file_type: fileType,
      offset: String(offset),
      limit: String(limit),
    })
    return request<BooksResponse>(`/api/books?${params}`)
  },
  duplicates: (kind: 'exact' | 'possible') =>
    request<DuplicatesResponse>(`/api/duplicates?kind=${kind}`),
  addFolder: (path: string) =>
    request<Folder>('/api/folders', {
      method: 'POST',
      body: JSON.stringify({ path }),
    }),
  rescan: (folderId: number) =>
    request<{ status: string }>(`/api/folders/${folderId}/scan`, {
      method: 'POST',
    }),
}
